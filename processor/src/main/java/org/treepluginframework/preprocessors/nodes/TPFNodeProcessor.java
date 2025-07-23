package org.treepluginframework.preprocessors.nodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import org.treepluginframework.annotations.*;
import org.treepluginframework.values.ClassMetadata;
import org.treepluginframework.values.MemberValueInfo;
import org.treepluginframework.values.TPFMetadataFile;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class TPFNodeProcessor extends AbstractProcessor {
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
    }

    //Make sure that a TPFNode can't also be marked as a resource.
    @Override
    public Set<String> getSupportedAnnotationTypes(){
        return Set.of("org.treepluginframework.annotations.TPFNode","org.treepluginframework.annotations.TPFResource","org.treepluginframework.annotations.TPFConstructor","org.treepluginframework.annotations.TPFValue");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {


        HashMap<TypeElement, ExecutableElement> constructors = new HashMap<>();
        handleTPFConstructor(constructors, roundEnv);
        //By this point, I know all the classes that are annotated with TPFConstructor.
        //Any other class could potentially not have it, so I still need to search.

        List<Element> tpfComponents = new ArrayList<>();
        tpfComponents.addAll(roundEnv.getElementsAnnotatedWith(TPFNode.class));
        tpfComponents.addAll(roundEnv.getElementsAnnotatedWith(TPFResource.class));
        HashMap<String, TypeElement> aliases = new HashMap<>();

        handleAliases(tpfComponents, aliases);

        HashMap<TypeElement, ExecutableElement> classConstructors = new HashMap<>();
        HashMap<TypeElement, List<TypeElement>> adjacencyList = new HashMap<>();

        TPFMetadataFile tpf_value_file = new TPFMetadataFile();

        if(!tpfComponents.isEmpty()){
            for(Element elem : tpfComponents){
                //For each class, find what the correct constructor for it should be.
                classConstructors.computeIfAbsent((TypeElement) elem, this::findCorrectConstructorOfType);
            }

            //Now I need to go through all the constructors, and figure out what TPFQualifier goes to, or what TPFPrimary goes to
            for(TypeElement classOfConstructor : classConstructors.keySet()){
                ExecutableElement constructor = classConstructors.get(classOfConstructor);
                //Adjacency list is how I find out about cycles, and it needs to be part of this.
                if(constructor == null) {
                    adjacencyList.put(classOfConstructor, new ArrayList<>());
                    continue;
                }

                List<? extends VariableElement> parameters = constructor.getParameters();
                if(!parameters.isEmpty())
                {
                    ClassMetadata clazz = new ClassMetadata();
                    for(VariableElement variable : parameters){
                        TypeMirror typeMirror = variable.asType();

                        // Check if it's primitive, can't take those in.
                        if (typeMirror.getKind().isPrimitive()) {

                            String wrapper = switch (typeMirror.getKind()) {
                                case BOOLEAN -> "java.lang.Boolean";
                                case BYTE    -> "java.lang.Byte";
                                case SHORT   -> "java.lang.Short";
                                case INT     -> "java.lang.Integer";
                                case LONG    -> "java.lang.Long";
                                case CHAR    -> "java.lang.Character";
                                case FLOAT   -> "java.lang.Float";
                                case DOUBLE  -> "java.lang.Double";
                                default      -> "corresponding wrapper class"; // fallback
                            };

                            processingEnv.getMessager().printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "Cannot take in primitives in the constructor. Use the wrapper class alternative instead, such as " + wrapper + ".",
                                    variable
                            );
                            continue;
                        }


                        // Get the Element representing the type
                        Element typeElement = processingEnv.getTypeUtils().asElement(typeMirror);
                        if (!(typeElement instanceof TypeElement)) {
                            // This might be an array, wildcard, etc.
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"Not a class type, skipping", variable);
                            continue;
                        }


                        TypeElement type = (TypeElement) typeElement;

                        if(variable.getAnnotation(TPFValue.class) != null){
                            TPFValue value = variable.getAnnotation(TPFValue.class);
                            clazz.parameters.put(variable.getSimpleName().toString(), new MemberValueInfo(type.getQualifiedName().toString(), value.location(), value.defaultValue()));
                        }

                        if (classOfConstructor.equals(type)) {
                            processingEnv.getMessager().printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "Class cannot depend on itself in constructor: " + classOfConstructor.getQualifiedName(),
                                    variable
                            );
                            continue;
                        }

                        Set<Modifier> modifiers = type.getModifiers();

                        boolean isInterface = type.getKind() == ElementKind.INTERFACE;
                        boolean isAbstract = modifiers.contains(Modifier.ABSTRACT);

                        TPFQualifier qualifierAnnotation = variable.getAnnotation(TPFQualifier.class);

                        TypeElement properClassType = null;
                        if(qualifierAnnotation != null){
                            properClassType = handleTPFQualifier(qualifierAnnotation, classOfConstructor, type, variable, aliases);
                        }
                        else
                        {
                            if(isInterface){
                                Types typeUtils = processingEnv.getTypeUtils();
                                List<TypeElement> potentialCandidates = new ArrayList<>();
                                List<TypeElement> primaryCandidates = new ArrayList<>();
                                for (Element candidateElement : tpfComponents) {
                                    if (!(candidateElement instanceof TypeElement candidateType)) continue;
                                    if (candidateType.getKind() != ElementKind.CLASS) continue;

                                    if (typeUtils.isAssignable(candidateType.asType(), variable.asType())) {
                                        if (candidateElement.getAnnotation(TPFPrimary.class) != null) {
                                            primaryCandidates.add(candidateType);
                                        } else {
                                            potentialCandidates.add(candidateType);
                                        }
                                        System.out.println("Found implementor: " + candidateType.getQualifiedName());
                                    }
                                }

                                if (!primaryCandidates.isEmpty()) {
                                    if (primaryCandidates.size() > 1) {
                                        StringBuilder errorMsg = new StringBuilder(classOfConstructor.getQualifiedName() + " is ambiguous due to multiple @TPFPrimary classes: ");
                                        for (TypeElement candidate : primaryCandidates) {
                                            errorMsg.append(candidate.getQualifiedName()).append(", ");
                                        }
                                        // Remove trailing comma and space safely
                                        if (errorMsg.length() > 2) errorMsg.setLength(errorMsg.length() - 2);
                                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, errorMsg.toString(), variable);
                                    } else {
                                        properClassType = primaryCandidates.get(0);
                                    }
                                } else if (!potentialCandidates.isEmpty()) {
                                    if (potentialCandidates.size() > 1) {
                                        StringBuilder errorMsg = new StringBuilder(classOfConstructor.getQualifiedName() + " is ambiguous, annotate a class with @TPFPrimary or annotate variable with @TPFQualifier to disambiguate: ");
                                        for (TypeElement candidate : potentialCandidates) {
                                            errorMsg.append(candidate.getQualifiedName()).append(", ");
                                        }
                                        if (errorMsg.length() > 2) errorMsg.setLength(errorMsg.length() - 2);
                                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, errorMsg.toString(), variable);
                                    } else {
                                        properClassType = potentialCandidates.get(0);
                                    }
                                } else {
                                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, classOfConstructor.getQualifiedName() + " has no valid implementations found", variable);
                                }
                            }
                            else if(isAbstract){
                                Types typeUtils = processingEnv.getTypeUtils();
                                List<TypeElement> potentialCandidates = new ArrayList<>();
                                List<TypeElement> primaryCandidates = new ArrayList<>();

                                for (Element candidateElement : tpfComponents) {
                                    if (!(candidateElement instanceof TypeElement candidateType)) continue;
                                    if (candidateType.getKind() != ElementKind.CLASS) continue;

                                    // Check if candidateType extends the abstract class (directly or indirectly)
                                    if (typeUtils.isAssignable(candidateType.asType(), type.asType())) {
                                        if (candidateType.getAnnotation(TPFPrimary.class) != null) {
                                            primaryCandidates.add(candidateType);
                                        } else {
                                            potentialCandidates.add(candidateType);
                                        }
                                        System.out.println("Found subclass: " + candidateType.getQualifiedName());
                                    }
                                }

                                if (!primaryCandidates.isEmpty()) {
                                    if (primaryCandidates.size() > 1) {
                                        StringBuilder errorMsg = new StringBuilder(classOfConstructor.getQualifiedName() + " is ambiguous due to multiple @TPFPrimary classes extending the abstract class: ");
                                        for (TypeElement candidate : primaryCandidates) {
                                            errorMsg.append(candidate.getQualifiedName()).append(", ");
                                        }
                                        if (errorMsg.length() > 2) errorMsg.setLength(errorMsg.length() - 2);
                                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, errorMsg.toString(), variable);
                                    } else {
                                        properClassType = primaryCandidates.get(0);
                                    }
                                } else if (!potentialCandidates.isEmpty()) {
                                    if (potentialCandidates.size() > 1) {
                                        StringBuilder errorMsg = new StringBuilder(classOfConstructor.getQualifiedName() + " is ambiguous, annotate a class with @TPFPrimary or annotate variable with @TPFQualifier to disambiguate: ");
                                        for (TypeElement candidate : potentialCandidates) {
                                            errorMsg.append(candidate.getQualifiedName()).append(", ");
                                        }
                                        if (errorMsg.length() > 2) errorMsg.setLength(errorMsg.length() - 2);
                                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, errorMsg.toString(), variable);
                                    } else {
                                        properClassType = potentialCandidates.get(0);
                                    }
                                } else {
                                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, classOfConstructor.getQualifiedName() + " has no valid subclasses found", variable);
                                }

                            }
                            else
                            {
                                properClassType = type;
                            }
                        }

                        if(properClassType != null){

                            if(properClassType.getAnnotation(TPFNode.class) == null && properClassType.getAnnotation(TPFResource.class) == null && variable.getAnnotation(TPFValue.class) == null)
                            {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"The expected class: " + properClassType.getQualifiedName() + " is not annotated with TPFNode, nor TPFResource. The variable isn't annotated with TPFValue either.", variable);
                            }

                            adjacencyList.computeIfAbsent(classOfConstructor, k -> new ArrayList<>()).add(properClassType);
                        }

                    }

                    //Handle the TPFValues here.
                    // Create example class metadata
                    if(!clazz.isEmpty())
                        tpf_value_file.classes.put(classOfConstructor.getQualifiedName().toString(), clazz);
                }
                else
                {
                    adjacencyList.computeIfAbsent(classOfConstructor,k->new ArrayList<>());
                }

            }


            if(!adjacencyList.isEmpty()){
                HashMap<String,List<String>> convert = new HashMap<>();
                for(TypeElement parent : adjacencyList.keySet()){
                    List<TypeElement> children = adjacencyList.get(parent);

                    List<String> conv = new ArrayList<>();
                    for(TypeElement child : children){
                        conv.add(child.getQualifiedName().toString());
                    }

                    convert.put(parent.getQualifiedName().toString(), conv);
                    String print = parent.getQualifiedName()+":";
                    print += children.stream()
                            .map(p->p.getQualifiedName().toString())
                            .collect(Collectors.joining(","));
                    System.out.println(print+"\n");
                }


                TopologicalSort.SortResult result = TopologicalSort.kahnTopologicalSort(adjacencyList);
                //TopologicalSort.StringSortResult result = TopologicalSort.kahnTopologicalSortString(convert);
                if (result.sortedList == null) {
                    StringBuilder cycleMsg = new StringBuilder("Cycle detected in dependencies between:\n");

                    for (TypeElement elem : result.cycleNodes) {
                        cycleMsg.append(" - ").append(elem.getQualifiedName()).append("\n");
                    }
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, cycleMsg.toString());
                }
                else
                {
                    Collections.reverse(result.sortedList);
                    try {
                        FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "","META-INF/tpf/createorder.txt");
                        try (Writer writer = file.openWriter()) {
                            result.sortedList.removeIf(elem -> !adjacencyList.containsKey(elem));

                            for(TypeElement elem : result.sortedList){
                                List<TypeElement> parameters = adjacencyList.get(elem);

                                if(!parameters.isEmpty()){
                                    String paramList = parameters.stream()
                                            .map(p->p.getQualifiedName().toString())
                                            .collect(Collectors.joining(","));
                                    writer.write(elem.getQualifiedName()+"("+paramList+")\n");
                                }
                                else
                                {
                                    writer.write(elem.getQualifiedName()+"()\n");
                                }

                            }
                        } catch (IOException e) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write META-INF file: " + e.getMessage());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        }

        /*
        System.out.println("What First?!");
        tpf_value_file.printMetadatFile();


        System.out.println("What Second?!");
        tpf_value_file.printMetadatFile();
        */

        Set<? extends Element> valueElements = roundEnv.getElementsAnnotatedWith(TPFValue.class);
        if(!valueElements.isEmpty())
        {
            for(Element element : valueElements){
                //The code above should have already handled anything that was parameters.
                //Now I need to deal with whatever fields there are.

                VariableElement field = (VariableElement) element;
                //Qualified_Class_Name:Field_Name<Field_Location:Default_Value>
                TPFValue annotation = field.getAnnotation(TPFValue.class);
                if(annotation.location().isEmpty()){
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"TPFValue annotation does not have a location",element);
                }

                TypeMirror type = field.asType();
                if (type.getKind().isPrimitive()) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "TPFValue annotations can't use primitives as values. Use the wrapper classes instead.",
                            element
                    );
                    continue;
                }

                Element variableElement = processingEnv.getTypeUtils().asElement(field.asType());
                TypeElement typeElement = (TypeElement) variableElement;
                if(element.getKind() == ElementKind.PARAMETER) continue;

                TypeElement classOfElement = (TypeElement) element.getEnclosingElement();
                ClassMetadata metadata = tpf_value_file.classes.getOrDefault(classOfElement.getQualifiedName().toString(), new ClassMetadata());
                metadata.fields.put(field.getSimpleName().toString(), new MemberValueInfo(typeElement.getQualifiedName().toString(), annotation.location(), annotation.defaultValue()));
                tpf_value_file.classes.put(classOfElement.getQualifiedName().toString(), metadata);
            }
        }

        if(!tpf_value_file.isEmpty()){

            //Loop through all of the classes. Then, I need to see their parameters, and see what type their TPFValue has.
            //If I find out that two different classes have the same location, but different value types, then throw an error.
            //tpf_value_file.printMetadatFile();
            HashSet<String> locations = new HashSet<>();
            for(String c : tpf_value_file.classes.keySet()){
                ClassMetadata meta = tpf_value_file.classes.get(c);


                for(String fieldName : meta.fields.keySet()){
                    MemberValueInfo info = meta.fields.get(fieldName);
                    locations.add(info.location);
                }

                for(String parameterName : meta.parameters.keySet()){
                    MemberValueInfo info = meta.parameters.get(parameterName);
                    locations.add(info.location);
                }
            }

            tpf_value_file.locations = locations;

            try {
                // Create resource file under META-INF/tpf/
                FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/tpf/values.json");
                try (Writer writer = file.openWriter()) {
                    // Serialize your object to JSON string (using Jackson or Gson)
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tpf_value_file);

                    // Write JSON string to the file
                    writer.write(jsonString);
                }
            } catch (IOException e) {
                e.printStackTrace();
                // handle or propagate error as appropriate
            }
        }



        return true;
    }

    private TypeElement handleTPFQualifier(TPFQualifier qualifierAnnotation, TypeElement classOfConstructor, TypeElement variableType, VariableElement variable, HashMap<String,TypeElement> aliases){
        //Check whatever the qualifier is against the type.
        // Try to get the TypeMirror from the specifiedClass safely
        TypeMirror specifiedType = null;
        try {
            qualifierAnnotation.specifiedClass(); // This line is only to trigger the exception
        } catch (MirroredTypeException e) {
            specifiedType = e.getTypeMirror();
        }

        // Determine if the specified class was left as the default Void.class
        boolean isVoid = specifiedType != null && specifiedType.toString().equals("java.lang.Void");

        if (isVoid && qualifierAnnotation.className().isEmpty()) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "TPFQualifier must use either specifiedClass, or className field",
                    variable
            );
            return null;
        }

        if(!isVoid && !qualifierAnnotation.className().isEmpty()){
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "TPFQualifier must use either specifiedClass, or className field",
                    variable
            );
            return null;
        }

        TypeElement correspondingClass = null;
        System.out.println("Hello world, what's going on?!");

        if(!isVoid){
            //One to one match.
            TypeMirror classTypeMirror = null;
            try {
                Class<?> clazz = qualifierAnnotation.specifiedClass(); // This will throw
            } catch (MirroredTypeException e) {
                classTypeMirror = e.getTypeMirror();
            }
            Element element = processingEnv.getTypeUtils().asElement(classTypeMirror);
            if (element instanceof TypeElement correctType) {
                // You now have the TypeElement representing the class from the annotation
                correspondingClass = correctType;
            }
        }
        else {
            String aliasOrClass = qualifierAnnotation.className();
            if (aliases.containsKey(aliasOrClass))
            {
                correspondingClass = aliases.get(aliasOrClass);
            }
            else
            {
                // Attempt to resolve it as a fully qualified class name
                correspondingClass = processingEnv.getElementUtils().getTypeElement(aliasOrClass);

                if (correspondingClass == null) {
                    // Not found — report an error
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Could not resolve class for className: " + aliasOrClass,
                            variable
                    );
                    return null;
                }
            }
        }

        //Make sure that correspondingClass is not an interface, and it's not an abstract class.
        if(correspondingClass == null){
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"Unable to parse the class TPFQualifier is searching for", variable);
            return null;
        }

        boolean correspondingIsInterface = correspondingClass.getKind() == ElementKind.INTERFACE;
        boolean correspondingIsAbstract = correspondingClass.getModifiers().contains(Modifier.ABSTRACT);

        if(correspondingIsAbstract || correspondingIsInterface)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"Specified class in TPFQualifier: " + correspondingClass.getQualifiedName() + " must be a concrete class. Cannot be abstract nor interface", variable);
            return null;
        }



        Types typeUtils = processingEnv.getTypeUtils();
        TypeMirror expectedType = variableType.asType();                // Interface or abstract class
        TypeMirror actualType = correspondingClass.asType();    // Concrete class to check


        if (variableType.getKind() == ElementKind.INTERFACE) {
            // Check if correspondingClass implements the interface
            if (!typeUtils.isAssignable(actualType, expectedType)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Class " + correspondingClass.getQualifiedName() +
                                " does not implement interface " + variableType.getQualifiedName(),
                        variable
                );
                return null;
            }
        } else{
            // Check if correspondingClass extends the abstract class
            if (!typeUtils.isAssignable(actualType, expectedType)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Class " + correspondingClass.getQualifiedName() +
                                " does not extend class " + variableType.getQualifiedName(),
                        variable
                );
                return null;
            }
        }

        return correspondingClass;
    }

    private ExecutableElement findCorrectConstructorOfType(TypeElement tpfComponent){
        //It's ok if there's no constructor, just throw a warning about that.
        List<ExecutableElement> potentialConstructors = new ArrayList<>();
        for(Element enclosed : tpfComponent.getEnclosedElements()){
            if(enclosed.getKind() != ElementKind.CONSTRUCTOR) continue;
            //Constructor with arguments takes priority over constructor without arguments.
            ExecutableElement constructor = (ExecutableElement) enclosed;

            boolean hasParameters = !constructor.getParameters().isEmpty();
            if(potentialConstructors.isEmpty()){
                potentialConstructors.add(constructor);
            }
            else {
                boolean topHasParameters = !potentialConstructors.getFirst().getParameters().isEmpty();
                if (!topHasParameters && hasParameters) {
                    potentialConstructors.clear();
                    potentialConstructors.add(constructor);
                }
                else if(topHasParameters && hasParameters)
                {
                    potentialConstructors.add(constructor);
                }
            }
        }

        System.out.println(tpfComponent.getQualifiedName() + " Potential: " + potentialConstructors);
        if(potentialConstructors.size() > 1){
            //Too many potential constructors, one needs the @TPFConstructor annotation.
            //Also gave priority to constructors with parameters over ones that don't.
            for(ExecutableElement potentialConstructor : potentialConstructors){
                TypeElement parentClass = (TypeElement) potentialConstructor.getEnclosingElement();

                String paramList = potentialConstructor.getParameters().stream()
                        .map(p -> p.asType().toString())
                        .collect(Collectors.joining(", "));

                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Ambiguous constructor: " + parentClass.getQualifiedName() + "(" + paramList + "). Use @TPFConstructor to disambiguate.",
                        potentialConstructor
                );
            }
        }
        else if(!potentialConstructors.isEmpty()){
            return potentialConstructors.getFirst();
        }

        return null;
    }

    private boolean handleAliases(List<Element> tpfComponents, HashMap<String,TypeElement> aliases){
        HashMap<String,List<TypeElement>> aliasVerify = new HashMap<>();
        for(Element element : tpfComponents)
        {
            TypeElement tpfComponent = (TypeElement) element;

            TPFNode tpfNodeAnnotation = tpfComponent.getAnnotation(TPFNode.class);
            TPFResource tpfResourceAnnotation = tpfComponent.getAnnotation(TPFResource.class);

            if(tpfNodeAnnotation != null && tpfResourceAnnotation != null){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "A class cannot have both the @TPFNode annotation and the @TPFResource.",tpfComponent);
            }

            //Get the alias
            String alias = (tpfNodeAnnotation != null) ? tpfNodeAnnotation.alias() : tpfResourceAnnotation.alias();

            //Making it case-insensitive, don't want to deal with that bug
            alias = alias.toLowerCase();

            if (!alias.isEmpty()) {
                aliasVerify.computeIfAbsent(alias, k -> new ArrayList<>()).add(tpfComponent);
            }
        }

        boolean failed = false;
        for(String key : aliasVerify.keySet()){
            List<TypeElement> potential = aliasVerify.get(key);
            if(potential.size() > 1){
                for(TypeElement t : potential){
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "The class " + t.getQualifiedName() + " has the same alias as another class: " + key, t);
                    failed = true;
                }
            }
            else
            {
                aliases.put(key, potential.getFirst());
            }
        }

        if(!failed){
            if(!aliases.isEmpty()){
                try {
                    FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "","META-INF/tpf/aliases.txt");
                    try (Writer writer = file.openWriter()) {
                        for(String alias : aliases.keySet()){
                            TypeElement type = aliases.get(alias);
                            writer.write(alias+":"+type.getQualifiedName()+"\n");
                        }
                    } catch (IOException e) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write META-INF file: " + e.getMessage());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return failed;
    }
    private boolean handleTPFConstructor(HashMap<TypeElement,ExecutableElement> constructors, RoundEnvironment roundEnv){

        Set<? extends Element> constructorElements = roundEnv.getElementsAnnotatedWith(TPFConstructor.class);

        if(!constructorElements.isEmpty()) {
            //Make sure that if a class has TPFConstructor, it's a TPFNode or TPFResource
            HashMap<TypeElement, List<ExecutableElement>> classesAndTPFConstructors = new HashMap<>();
            //Gather all the potential constructors first.
            for (Element elem : constructorElements) {
                TypeElement classEnclosed = (TypeElement) elem.getEnclosingElement();
                if (classEnclosed.getAnnotation(TPFNode.class) == null && classEnclosed.getAnnotation(TPFResource.class) == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "TPFConstructor must be used on classes annotated with TPFNode or TPFResource", elem);
                }

                classesAndTPFConstructors.computeIfAbsent(classEnclosed, k -> new ArrayList<ExecutableElement>()).add((ExecutableElement) elem);
            }

            boolean failed = false;
            for (Map.Entry<TypeElement, List<ExecutableElement>> entry : classesAndTPFConstructors.entrySet()) {
                TypeElement enclosingClass = entry.getKey();
                List<ExecutableElement> possibleConstructors = entry.getValue();

                //If a class is annotated with TPFConstructor more than once, no bueno.
                if (possibleConstructors.size() > 1) {
                    for (ExecutableElement element : possibleConstructors) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "The class " + enclosingClass.getQualifiedName() + " cannot have more than one constructor annotated with @TPFConstruct", element);
                        failed = true;
                    }
                }
                else if(possibleConstructors.size() == 1)
                {
                    constructors.put(enclosingClass, possibleConstructors.getFirst());
                }
            }

            return true;
        }
        return false;
    }
    private class TopologicalSort {

        public static StringSortResult kahnTopologicalSortString(HashMap<String, List<String>> adjacencyList) {
            Map<String, Integer> inDegree = new HashMap<>();

            // Initialize in-degrees
            for (String node : adjacencyList.keySet()) {
                inDegree.putIfAbsent(node, 0);
                for (String neighbor : adjacencyList.get(node)) {
                    inDegree.put(neighbor, inDegree.getOrDefault(neighbor, 0) + 1);
                }
            }

            // PriorityQueue for deterministic order (alphabetical)
            PriorityQueue<String> queue = new PriorityQueue<>();

            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.offer(entry.getKey());
                }
            }

            List<String> sortedList = new ArrayList<>();
            while (!queue.isEmpty()) {
                String current = queue.poll();
                sortedList.add(current);

                for (String neighbor : adjacencyList.getOrDefault(current, Collections.emptyList())) {
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                    if (inDegree.get(neighbor) == 0) {
                        queue.offer(neighbor);
                    }
                }
            }

            // If not all nodes are in sorted list, there's a cycle
            if (sortedList.size() != inDegree.size()) {
                List<String> cycleNodes = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                    if (entry.getValue() > 0) {
                        cycleNodes.add(entry.getKey());
                    }
                }
                return new StringSortResult(null, cycleNodes);
            }

            return new StringSortResult(sortedList, null);
        }


        public static SortResult kahnTopologicalSort(HashMap<TypeElement, List<TypeElement>> adjacencyList) {
            Map<TypeElement, Integer> inDegree = new HashMap<>();

            for (TypeElement node : adjacencyList.keySet()) {
                inDegree.putIfAbsent(node, 0);
                for (TypeElement neighbor : adjacencyList.get(node)) {
                    inDegree.put(neighbor, inDegree.getOrDefault(neighbor, 0) + 1);
                }
            }

            //Makes it deterministic, so it'll always choose the first alphabetically if given the chance.
            PriorityQueue<TypeElement> queue = new PriorityQueue<>(new Comparator<TypeElement>() {
                @Override
                public int compare(TypeElement o1, TypeElement o2) {
                    return o1.getSimpleName().toString().compareTo(o2.getSimpleName().toString());
                }
            });

            for (Map.Entry<TypeElement, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.offer(entry.getKey());
                }
            }

            List<TypeElement> sortedList = new ArrayList<>();
            while (!queue.isEmpty()) {
                TypeElement current = queue.poll();
                sortedList.add(current);

                for (TypeElement neighbor : adjacencyList.getOrDefault(current, Collections.emptyList())) {
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                    if (inDegree.get(neighbor) == 0) {
                        queue.offer(neighbor);
                    }
                }
            }

            if (sortedList.size() != inDegree.size()) {
                // Nodes with non-zero in-degree are in a cycle
                List<TypeElement> cycleNodes = new ArrayList<>();
                for (Map.Entry<TypeElement, Integer> entry : inDegree.entrySet()) {
                    if (entry.getValue() > 0) {
                        cycleNodes.add(entry.getKey());
                    }
                }
                return new SortResult(null, cycleNodes);
            }

            return new SortResult(sortedList, null);
        }

        private static class StringSortResult{
            public final List<String> sortedList;
            public final List<String> cycleNodes;
            public StringSortResult(List<String> sortedList, List<String> cycleNodes){
                this.sortedList = sortedList;
                this.cycleNodes = cycleNodes;
            }
        }

        private static class SortResult {
            public final List<TypeElement> sortedList;
            public final List<TypeElement> cycleNodes;

            public SortResult(List<TypeElement> sortedList, List<TypeElement> cycleNodes) {
                this.sortedList = sortedList;
                this.cycleNodes = cycleNodes;
            }
        }

    }
}
