package org.treepluginframework.preprocessors.nodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import org.checkerframework.checker.units.qual.C;
import org.treepluginframework.annotations.*;
import org.treepluginframework.component_architecture.TPF;
import org.treepluginframework.values.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class TPFNodeProcessor extends AbstractProcessor {
    private Filer filer;
    private int count = 0;
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
    }

    //Make sure that a TPFNode can't also be marked as a resource.
    @Override
    public Set<String> getSupportedAnnotationTypes(){
        return Set.of("org.treepluginframework.annotations.TPFConstructor","org.treepluginframework.annotations.TPFNode","org.treepluginframework.annotations.TPFResource","org.treepluginframework.annotations.TPFValue","org.treepluginframework.annotations.TPFPrimary","org.treepluginframework.annotations.TPFQualifier");
    }

    private boolean preventNodeAndResourceAnnotation(List<Element> tpfElements){
        boolean hasDouble = false;

        Set<Element> seen = new HashSet<>();
        Set<Element> duplicates = new HashSet<>();

        for(Element item : tpfElements){
            if(!seen.add(item)){
                duplicates.add(item);
                TypeElement clazz = (TypeElement) item;
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,clazz.getQualifiedName() + " cannot have both the TPFNode and TPFResource annotations.",item);
                hasDouble = true;
            }
        }

        return hasDouble;
    }

    private String getQualifiedTypeName(TypeMirror type, Types typeUtils) {
        switch (type.getKind()) {
            case DECLARED -> {
                // Regular object type
                TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
                return typeElement.getQualifiedName().toString();
            }
            case ARRAY -> {
                // Handle array types recursively
                ArrayType arrayType = (ArrayType) type;
                return getQualifiedTypeName(arrayType.getComponentType(), typeUtils) + "[]";
            }
            default -> {
                return type.toString();
                /*
                if (type.getKind().isPrimitive()) {
                    // Use boxed type if needed
                    TypeElement boxed = typeUtils.boxedClass((PrimitiveType) type);
                    return boxed.getQualifiedName().toString(); // use `type.toString()` if you want raw primitive name
                } else {
                    return type.toString(); // fallback (wildcards, type vars, etc.)
                }
                */
            }
        }
    }


    //Constructor can see if this method already handled it for that class, and just skip that parameter.
    //The errorOccurred stuff is just so that all bad TPFValue annotations are taken care of immediately.
    private boolean handleTPFValue(HashMap<TypeElement, ClassValueMetadata> allClassData, Set<VariableElement> usedVariables, Set<String> configLocations, RoundEnvironment roundEnv){
        //Check the values first, and translate if necessary.
        //I store an executable hashmap?
        List<Element> valueVariables = new ArrayList<>(roundEnv.getElementsAnnotatedWith(TPFValue.class));
        boolean errorOccurred = false;
        if(!valueVariables.isEmpty()){
            HashMap<ExecutableElement, String> constructorSignatures = new HashMap<>();

            for(Element elem : valueVariables) {
                boolean isParameter = (elem.getEnclosingElement().getKind() == ElementKind.CONSTRUCTOR);
                VariableElement variableElement = (VariableElement) elem;
                TypeMirror varType = variableElement.asType();

                Element current = variableElement;
                while (current != null && !(current.getKind().isClass() || current.getKind().isInterface())) {
                    current = current.getEnclosingElement();
                }
                TypeElement owningClass = (TypeElement) current;
                System.out.println("TPFValue in class " + owningClass.getQualifiedName());

                ClassValueMetadata classData = allClassData.computeIfAbsent(owningClass, k -> new ClassValueMetadata());
                TPFValue valueAnnotation = variableElement.getAnnotation(TPFValue.class);


                if(valueAnnotation.location().isEmpty() || valueAnnotation.location().isBlank()){
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "TPFValue annotations must provide a location", elem);
                    errorOccurred = true;
                }

                String type = getQualifiedTypeName(varType, processingEnv.getTypeUtils());

                System.out.println(owningClass.getQualifiedName() + ": " + variableElement.getSimpleName() + " IsParameter: " + isParameter);
                if(isParameter){
                    //I need to get the Method signature of this executable.
                    ExecutableElement constructor = (ExecutableElement) variableElement.getEnclosingElement();
                    if(!constructorSignatures.containsKey(constructor))
                    {
                        String constructorSig = constructorSignatures.computeIfAbsent(constructor, c ->
                                c.getParameters().stream()
                                        .map(param -> "(" + param.asType().toString() + ")")
                                        .collect(Collectors.joining(",", "[", "]"))
                        );
                        constructorSignatures.put(constructor, constructorSig);
                    }

                    String constructorSig = constructorSignatures.get(constructor);
                    List<ParameterValueInfo> paramInfo = classData.parameters.computeIfAbsent(constructorSig, k -> new ArrayList<>());

                    List<? extends VariableElement> params = constructor.getParameters();

                    // Find the index of the parameter in the constructor's parameter list
                    int positionInConstructor = -1;
                    for (int i = 0; i < params.size(); i++) {
                        if (params.get(i).equals(variableElement)) {
                            positionInConstructor = i;
                            break;
                        }
                    }

                    paramInfo.add(new ParameterValueInfo(type, valueAnnotation.location(), valueAnnotation.defaultValue(), positionInConstructor));
                }
                else
                {
                    classData.fields.put(variableElement.getSimpleName().toString(), new FieldValueInfo(type, valueAnnotation.location(), valueAnnotation.defaultValue()));
                }
                configLocations.add(valueAnnotation.location());
                usedVariables.add(variableElement);
            }
        }

        return errorOccurred;
    }

    private boolean handleAliases(List<Element> tpfComponents, HashMap<String,TypeElement> aliases){
        HashMap<String,List<TypeElement>> aliasVerify = new HashMap<>();
        boolean errorOccurred = false;
        for(Element element : tpfComponents)
        {
            TypeElement tpfComponent = (TypeElement) element;

            TPFNode tpfNodeAnnotation = tpfComponent.getAnnotation(TPFNode.class);
            TPFResource tpfResourceAnnotation = tpfComponent.getAnnotation(TPFResource.class);
            //I already handled the case where a node is both a node and a resource, so no need to check for both here.

            String alias = (tpfNodeAnnotation != null) ? tpfNodeAnnotation.alias() : tpfResourceAnnotation.alias();

            //Making it case-insensitive, don't want to deal with that bug
            alias = alias.toLowerCase();

            if (!alias.isEmpty()) {
                aliasVerify.computeIfAbsent(alias, k -> new ArrayList<>()).add(tpfComponent);
            }
        }

        for(String key : aliasVerify.keySet()){
            List<TypeElement> potential = aliasVerify.get(key);
            if(potential.size() > 1){
                for(TypeElement t : potential){
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "The class " + t.getQualifiedName() + " has the same alias as another class: " + key, t);
                    errorOccurred = true;
                }
            }
            else
            {
                aliases.put(key, potential.getFirst());
            }
        }

        return errorOccurred;
    }

    private boolean handleTPFQualifier(HashMap<String,TypeElement> aliases, HashMap<VariableElement, TypeElement> qualifierClassTranslations,RoundEnvironment round){
        boolean errorOccurred = false;
        Set<? extends Element> qualifiers = round.getElementsAnnotatedWith(TPFQualifier.class);

        if(qualifiers.isEmpty()) return false;

        for(Element elem : qualifiers){
            VariableElement variableElement = (VariableElement) elem;
            TypeMirror varType = variableElement.asType();

            if(varType.getKind().isPrimitive()){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"TPFQualifier cannot be used on variables with primitive types", elem);
                errorOccurred = true;
                continue;
            }

            TypeElement variableType = (TypeElement) processingEnv.getTypeUtils().asElement(varType);



            //I can guard against primitives immediately, but I can't guard against abstract classes nor interfaces immediately.

            /*
            Element current = variableElement;
            while (current != null && !(current.getKind().isClass() || current.getKind().isInterface())) {
                current = current.getEnclosingElement();
            }
            TypeElement owningClass = (TypeElement) current;
            */

            TPFQualifier qualifierAnnotation = variableElement.getAnnotation(TPFQualifier.class);
            //Make sure that the types that are present are compatible with each other.
            //If they aren't throw a problem.

            // Check if the specified class is Void.class
            TypeMirror voidType = processingEnv.getElementUtils()
                    .getTypeElement("java.lang.Void")
                    .asType();

            TypeMirror specifiedType = null;

            try {
                // This will always throw at compile-time
                Class<?> ignored = qualifierAnnotation.specifiedClass();
            } catch (MirroredTypeException e) {
                specifiedType = e.getTypeMirror();
            }

            TypeElement classToCompare = null;
            if(specifiedType != null && !processingEnv.getTypeUtils().isSameType(specifiedType,voidType)){

                if(specifiedType.getKind().isPrimitive()){
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "The type of desired class cannot be a primitive." + specifiedType.toString(), elem);
                    errorOccurred = true;
                    continue;
                }

                classToCompare = (TypeElement) processingEnv.getTypeUtils().asElement(specifiedType);
            }
            else
            {
                //Aliases already has it
                if(aliases.containsKey(qualifierAnnotation.className())){
                    classToCompare = aliases.get(qualifierAnnotation.className());
                }
                else
                {
                    //it may be a qualified class name.
                    classToCompare = processingEnv.getElementUtils().getTypeElement(qualifierAnnotation.className());
                }
            }

            if(classToCompare == null){
                errorOccurred = true;
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to resolve the type TPFQualifier is asking for (Direct:" + specifiedType + " ClassName: " + qualifierAnnotation.className() + ")",elem);
                continue;
            }

            if(classToCompare.getAnnotation(TPFNode.class) == null && classToCompare.getAnnotation(TPFResource.class) == null){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"The class " + classToCompare.getQualifiedName() + " is not annotated with TPFNode nor TPFResource.", variableElement);
                errorOccurred = true;
                continue;
            }

            Set<Modifier> modifiers = classToCompare.getModifiers();

            boolean isInterface = classToCompare.getKind() == ElementKind.INTERFACE;
            boolean isAbstract = modifiers.contains(Modifier.ABSTRACT);


            if(isAbstract){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Specified class must be a concrete implementation, got Abstract class " + classToCompare.getQualifiedName() + " instead.", elem);
                errorOccurred = true;
                continue;
            }
            if(isInterface){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Specified class must be a concrete implementation, got Interface " + classToCompare.getQualifiedName() + " instead.", elem);
                errorOccurred = true;
                continue;
            }

            Types typeUtils = processingEnv.getTypeUtils();

            if (!typeUtils.isAssignable(classToCompare.asType(), variableType.asType())) {
                // A is a subtype of B (either extends or implements)
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"The desired class " + classToCompare.getQualifiedName() + " is incompatible with " + variableType.getQualifiedName(), elem);
                errorOccurred = true;
                continue;
            }

            //By this point, I know that whatever desired class is compatible.
            qualifierClassTranslations.put(variableElement, classToCompare);
        }

        return errorOccurred;
    }

    private boolean handleTPFConstructors(List<Element> tpfComponents, HashMap<TypeElement, ConstructorInformation> constructorInformation, Set<VariableElement> tpfValueElements, HashMap<VariableElement, TypeElement> qualifierClassTranslations, HashMap<TypeElement, List<TypeElement>> adjacencyList){
        if(tpfComponents.isEmpty()){
            return false;
        }

        boolean errorOccurred = false;

        for(Element elem : tpfComponents){
            TypeElement clazz = (TypeElement) elem;
            List<? extends Element> enclosed = clazz.getEnclosedElements();
            List<ExecutableElement> potentialConstructors = new ArrayList<>();

            List<ExecutableElement> priorityConstructors = new ArrayList<>();
            ExecutableElement noArgsConstructor = null;

            //First, find the correct constructor
            for(Element enclosedElement : enclosed)
            {
                if(enclosedElement.getKind() != ElementKind.CONSTRUCTOR) continue;
                if(enclosedElement.getAnnotation(TPFConstructor.class) != null)
                {
                    priorityConstructors.add((ExecutableElement) enclosedElement);
                }
                else
                {
                    ExecutableElement construct = (ExecutableElement) enclosedElement;
                    potentialConstructors.add(construct);

                    if(construct.getParameters().isEmpty()){
                        noArgsConstructor = construct;
                    }
                }
            }

            ExecutableElement correctConstructor = null;
            if(!priorityConstructors.isEmpty()){
                if(priorityConstructors.size() > 1){
                    errorOccurred = true;
                    for(ExecutableElement c : priorityConstructors){
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, clazz.getQualifiedName() + " has more than one constructor marked with TPFConstructor.", c);
                    }
                }
                else
                {
                    correctConstructor = priorityConstructors.getFirst();
                }
            }
            else if(!potentialConstructors.isEmpty())
            {
                if(noArgsConstructor != null){
                    correctConstructor = noArgsConstructor;
                }
                else if(potentialConstructors.size() > 1)
                {
                    errorOccurred = true;
                    for(ExecutableElement constructor : potentialConstructors){
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, clazz.getQualifiedName() + " has ambiguous constructors. Mark one with TPFConstructor, or include a no-args constructor.", constructor);
                    }
                }
                else
                {
                    correctConstructor = potentialConstructors.getFirst();
                }
            }

            //If there a correct constructor, verify its parameters.
            List<TypeElement> dependencies = adjacencyList.computeIfAbsent(clazz, k -> new ArrayList<>());
            if(correctConstructor != null && !correctConstructor.getParameters().isEmpty()){
                //If the constructor is empty, there's nothing to do here.
                if(correctConstructor.getParameters().isEmpty()) continue;

                //At this point, I can use the translations to see what's up.
                //NeededParameterTypes is going to be used later to figure out info.
                List<String> neededParameterTypes = new ArrayList<>();

                List<String> desiredParameterTypes = new ArrayList<>();
                for(VariableElement parameter : correctConstructor.getParameters()){
                    // Get the type of the parameter as a TypeMirror
                    TypeMirror paramType = parameter.asType();
                    // Convert the type to its string representation (e.g. "java.lang.String", "int")
                    String typeName = paramType.toString();
                    neededParameterTypes.add(typeName);

                    //TPFValue already handled this variable
                    //Prevents having to recheck that logic here.
                    //From this point on, if this parameter is a primitive, call an error.
                    if(tpfValueElements.contains(parameter)){
                        desiredParameterTypes.add(typeName);
                        continue;
                    }

                    if(paramType.getKind().isPrimitive())
                    {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Constructors for TPFNode and TPFResource cannot take primitives without the TPFValue annotation", parameter);
                        errorOccurred = true;
                        continue;
                    }

                     //If TPFQualifier found a translation, just add that to the desired.
                    if(qualifierClassTranslations.containsKey(parameter))
                    {
                        desiredParameterTypes.add(qualifierClassTranslations.get(parameter).getQualifiedName().toString());
                        dependencies.add(qualifierClassTranslations.get(parameter));
                        continue;
                    }



                    TypeElement parameterType = (TypeElement) processingEnv.getTypeUtils().asElement(paramType);

                    if(parameterType.getAnnotation(TPFNode.class) == null && parameterType.getAnnotation(TPFResource.class) == null)
                    {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Parameters for TPFNode/Resource main constructor must be TPFNode/Resources themselves, or be annotated with TPFValue: " + paramType.getClass().getCanonicalName(), parameter);
                        errorOccurred = true;
                        continue;
                    }

                    //If it didn't, now I manually have to go through and see what I could use instead.
                    //If ambiguous, throw an error.
                    boolean isInterface = parameterType.getKind() == ElementKind.INTERFACE;
                    boolean isAbstract = parameterType.getModifiers().contains(Modifier.ABSTRACT);

                    TypeElement desiredClass = null;
                    if(!isInterface && !isAbstract){
                        desiredClass = parameterType;
                    }
                    else
                    {
                        List<TypeElement> primaryPotentialClasses = new ArrayList<>();
                        List<TypeElement> potentialClasses = new ArrayList<>();

                        System.out.println("Abstract/Interface found: " + parameterType.getQualifiedName());
                        for(Element pC : tpfComponents){
                            //Don't consider yourself as a potential class
                            if(pC == elem) continue;
                            TypeElement potentialClass = (TypeElement) pC;

                            //!typeUtils.isAssignable(variableType.asType(), classToCompare.asType()
                            Types typeUtils = processingEnv.getTypeUtils();
                            if(typeUtils.isAssignable(potentialClass.asType(), parameterType.asType()))
                            {
                                if(potentialClass.getAnnotation(TPFPrimary.class) != null){
                                    primaryPotentialClasses.add(potentialClass);
                                }
                                else
                                {
                                    potentialClasses.add(potentialClass);
                                }
                            }
                        }

                        System.out.println("Size of potentials: " + primaryPotentialClasses.size() + " " + potentialClasses.size());

                        if(!primaryPotentialClasses.isEmpty()){
                            if(primaryPotentialClasses.size() > 1){

                                String errorTypes = primaryPotentialClasses.stream()
                                        .map(p -> p.getQualifiedName().toString())
                                        .collect(Collectors.joining(", "));


                                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"The type for the class " + parameterType.getQualifiedName().toString() + " is ambiguous since more than one implementation uses TPFPrimary: " + errorTypes, parameter);
                                errorOccurred = true;
                                continue;
                            }
                            else
                            {
                                desiredClass = primaryPotentialClasses.getFirst();
                            }
                        }
                        else if(!potentialClasses.isEmpty()){
                            if(potentialClasses.size() > 1){


                                String errorTypes = potentialClasses.stream()
                                        .map(p -> p.getQualifiedName().toString())
                                        .collect(Collectors.joining(", "));

                                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"The type for the class " + parameterType.getQualifiedName() + " is ambiguous. Utilize TPFPrimary or TPFQualifier as needed: " + errorTypes, parameter);
                                errorOccurred = true;
                                continue;
                            }
                            else
                            {
                                desiredClass = potentialClasses.getFirst();
                            }
                        }
                        else
                        {
                            //No potential classes.
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "No potential candidates were found for the Abstract/Interface type???? " + parameterType.getQualifiedName().toString() + " Count: " + tpfComponents.size(), parameter);
                            errorOccurred = true;
                            continue;
                        }
                    }

                    if(desiredClass == null){
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find a matching class definition for " + parameterType.getQualifiedName(), parameter);
                        errorOccurred = true;
                        continue;
                    }

                    desiredParameterTypes.add(desiredClass.getQualifiedName().toString());
                    dependencies.add(desiredClass);

                    //Just in case the dependency itself never uses the constructor stuff, so that it doesn't show up null.
                    //allClassData.computeIfAbsent(desiredClass, k-> new ClassValueMetadata());

                    //Just in case the dependency itself never uses the constructor stuff, make sure to still have it around.
                    //Bad, don't do this, since it can add constructors to classes that don't exist.
                    //constructorInformation.computeIfAbsent(desiredClass, k->new ConstructorInformation());
                }

                //Add the dependencies to the class Metadata.
                ConstructorInformation info = new ConstructorInformation();
                info.neededConstructorParameters = neededParameterTypes;
                info.desiredConstructorParameters = desiredParameterTypes;
                constructorInformation.put(clazz, info);
            }
            else
            {
                System.out.println("GOD HELP: " + clazz.getQualifiedName());
                //Even if it has a blank constructor, still give it metadata, since it'll be used for later.
                constructorInformation.put(clazz, new ConstructorInformation());
            }
        }

        return errorOccurred;
    }

    private TPFMetadataFile findTPFValuesFile(){
        try(InputStream is = TPF.class.getClassLoader()
                .getResourceAsStream("META-INF/tpf/metadata.json")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                TPFMetadataFile metaFile = mapper.readValue(is, TPFMetadataFile.class);
                return metaFile;
            }
        } catch (IOException e) {
            return null;
            //throw new RuntimeException(e);
        }
        return null;
    }

    private void writeTPFFile(TPFMetadataFile metaFile){
        try {
            // Create resource file under META-INF/tpf/
            FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/tpf/metadata.json");
            try (Writer writer = file.openWriter()) {
                // Serialize your object to JSON string (using Jackson or Gson)
                ObjectMapper mapper = new ObjectMapper();
                String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metaFile);

                // Write JSON string to the file
                writer.write(jsonString);
            }
        } catch (IOException e) {
            e.printStackTrace();
            // handle or propagate error as appropriate
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if(roundEnv.processingOver()) return false;


        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Processing round. Over=" + roundEnv.processingOver() + ", Annotations=" + roundEnv.getRootElements().size() + " Count: " + count);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "\t " +roundEnv.getRootElements());
        List<Element> tpfComponents = new ArrayList<>();
        List<Element> nodeElements = new ArrayList<>(roundEnv.getElementsAnnotatedWith(TPFNode.class));
        List<Element> resourceElements = new ArrayList<>(roundEnv.getElementsAnnotatedWith(TPFResource.class));

        tpfComponents.addAll(nodeElements);
        tpfComponents.addAll(resourceElements);

        List<Element> valueElements = new ArrayList<>(roundEnv.getElementsAnnotatedWith(TPFValue.class));

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,"TPF Component Count: " + tpfComponents.size());


        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,"\t"+nodeElements.toString());
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,"\t"+resourceElements.toString());

        //TODO: Doesn't account for TPFValue or other annotations, take care here.
        if(tpfComponents.isEmpty() && valueElements.isEmpty()){
            return false;
        }


        boolean encounteredDoubleAnnotation = preventNodeAndResourceAnnotation(tpfComponents);
        if(encounteredDoubleAnnotation){
            return true;
        }

        HashMap<String, TypeElement> aliases = new HashMap<>();
        boolean errorWithAlias = handleAliases(tpfComponents, aliases);

        if(errorWithAlias){
            return true;
        }

        HashMap<TypeElement, ClassValueMetadata> allClassData = new HashMap<>();

        Set<VariableElement> tpfValueVariables = new HashSet<>();
        HashSet<String> configLocations = new HashSet<>();
        boolean tpfValueErrorOccurred = handleTPFValue(allClassData,tpfValueVariables, configLocations, roundEnv);

        if(tpfValueErrorOccurred){
            return true;
        }

        //HashMap<String,TypeElement> aliases, HashMap<VariableElement, TypeElement> qualifierClassTranslations,RoundEnvironment round
        HashMap<VariableElement, TypeElement> qualifierClassTranslations = new HashMap<>();
        boolean tpfQualifierErrorOccurred = handleTPFQualifier(aliases, qualifierClassTranslations, roundEnv);

        if(tpfQualifierErrorOccurred){
            return true;
        }

        HashMap<TypeElement,List<TypeElement>> adjacencyList = new HashMap<>();
        HashMap<TypeElement,ConstructorInformation> constructorInformation = new LinkedHashMap<>();
        boolean constructorErrorOccurred = handleTPFConstructors(tpfComponents, constructorInformation,  tpfValueVariables, qualifierClassTranslations, adjacencyList);

        if(constructorErrorOccurred){
            return true;
        }

        TopologicalSort.SortResult sortResult = TopologicalSort.kahnTopologicalSort(adjacencyList);
        if(sortResult.sortedList == null){
            StringBuilder cycleMsg = new StringBuilder("Cycle detected in dependencies between:\n");

            for (TypeElement elem : new LinkedHashSet<>(sortResult.cycleNodes)) { // preserve order
                cycleMsg.append(" - ").append(elem.getQualifiedName()).append("\n");
            }

            cycleMsg.append("\nCycle path:\n    ");

            String cyclePath = sortResult.cycleNodes.stream()
                    .map(elem -> elem.getQualifiedName().toString())
                    .collect(Collectors.joining(" → "));

            cycleMsg.append(cyclePath);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, cycleMsg.toString());
            return true;
        }
        else
        {
            System.out.println("Options for sorting: " + sortResult.sortedList.size());
            HashMap<String, ClassValueMetadata> valueInformation = new HashMap<>();
            LinkedHashMap<String, ConstructorInformation> constructorCreationOrder = new LinkedHashMap<>();

            Collections.reverse(sortResult.sortedList);

            for(TypeElement key : allClassData.keySet())
            {
                valueInformation.put(key.getQualifiedName().toString(), allClassData.get(key));
            }

            for(TypeElement clazz : sortResult.sortedList){
                constructorCreationOrder.put(clazz.getQualifiedName().toString(), constructorInformation.get(clazz));
            }

            TPFMetadataFile metaFile = new TPFMetadataFile(valueInformation, constructorCreationOrder, configLocations);
            //Serialize this metaFile now.
            writeTPFFile(metaFile);
        }

        return true;
    }

    private class TopologicalSort {

        /*
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
        */

        public static SortResult kahnTopologicalSort(HashMap<TypeElement, List<TypeElement>> adjacencyList) {
            Map<TypeElement, Integer> inDegree = new HashMap<>();

            for (TypeElement node : adjacencyList.keySet()) {
                inDegree.putIfAbsent(node, 0);
                for (TypeElement neighbor : adjacencyList.get(node)) {
                    inDegree.put(neighbor, inDegree.getOrDefault(neighbor, 0) + 1);
                }
            }

            PriorityQueue<TypeElement> queue = new PriorityQueue<>(Comparator.comparing(o -> o.getSimpleName().toString()));

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
                // Cycle exists; extract real cycle path
                List<TypeElement> cyclePath = findCycleDFS(adjacencyList);
                return new SortResult(null, cyclePath);
            }

            return new SortResult(sortedList, null);
        }


        // Helper to extract the cycle path using DFS
        private static List<TypeElement> findCycleDFS(Map<TypeElement, List<TypeElement>> graph) {
            Set<TypeElement> visited = new HashSet<>();
            Set<TypeElement> recStack = new HashSet<>();
            Deque<TypeElement> path = new ArrayDeque<>();

            for (TypeElement node : graph.keySet()) {
                if (dfsCycle(node, graph, visited, recStack, path)) {
                    // Cycle has been detected; extract it
                    TypeElement start = path.peek(); // node where cycle was detected
                    List<TypeElement> reversedPath = new ArrayList<>(path);
                    Collections.reverse(reversedPath);

                    List<TypeElement> cycle = new ArrayList<>();
                    for (TypeElement elem : reversedPath) {
                        cycle.add(elem);
                        if (elem.equals(start) && cycle.size() > 1) break;
                    }
                    Collections.reverse(cycle); // Optional: keep original direction
                    cycle.add(start); // Close the cycle explicitly
                    return cycle;
                }
            }
            return null;
        }

        private static boolean dfsCycle(TypeElement node,
                                        Map<TypeElement, List<TypeElement>> graph,
                                        Set<TypeElement> visited,
                                        Set<TypeElement> recStack,
                                        Deque<TypeElement> path) {
            if (recStack.contains(node)) {
                return true;
            }

            if (visited.contains(node)) return false;

            visited.add(node);
            recStack.add(node);
            path.push(node);

            for (TypeElement neighbor : graph.getOrDefault(node, List.of())) {
                if (dfsCycle(neighbor, graph, visited, recStack, path)) {
                    return true;
                }
            }

            recStack.remove(node);
            path.pop();
            return false;
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
