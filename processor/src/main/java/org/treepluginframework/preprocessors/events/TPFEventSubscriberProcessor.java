package org.treepluginframework.preprocessors.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.annotations.MetaEventSubscription;
import org.treepluginframework.annotations.TPFMetaEventListener;
import org.treepluginframework.annotations.TPFNode;
import org.treepluginframework.component_architecture.DAG;
import org.treepluginframework.meta_events.TPFMetaEvent;
import org.treepluginframework.values.MethodSignature;
import org.treepluginframework.values.TPFEventFile;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class TPFEventSubscriberProcessor extends AbstractProcessor {
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
    }

    //Make sure that a TPFNode can't also be marked as a resource.
    @Override
    public Set<String> getSupportedAnnotationTypes(){
        return Set.of("org.treepluginframework.annotations.EventSubscription","org.treepluginframework.annotations.MetaEventSubscription");
    }

    Set<TypeElement> allTypes = new HashSet<>();

    //If a class have both EventSubscription, and MetaEventSubscription, I need to throw an error. You should only have one or the other.
    //If a class is annotated with TPFNode, if any method is annotated with MetaEventSubscription, throw an error.
    //If a class is annotated with TPFMetaEventListener, if any method is annotated with EventSubscription, throw an error.

    //Since a class doesn't need to be annotated with TPFNode or TPFMetaEventListener to have subscriptions,
    //I need to throw the errors here, in the EventSubscriberProcessor, can't do it the NodeProcessor.

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv){
        for(Element m : roundEnv.getRootElements()){
            if(m instanceof TypeElement type){
                allTypes.add(type);
            }
        }

        if(roundEnv.processingOver()){
            System.out.println("Did you get here?:" + allTypes.size());
            DAG<TypeElement> dg = new DAG<>();//DAG.regular();//new DAG<TypeElement>();
            Types typeUtils = processingEnv.getTypeUtils();

            for(TypeElement type : allTypes){
                TypeMirror superMirror = type.getSuperclass();
                TypeElement superType = null;
                if(superMirror.getKind() != TypeKind.NONE){
                    superType = (TypeElement) typeUtils.asElement(superMirror);
                }
                System.out.println("Whats the super type of current: " + type +" , " + ((superType == null) ? "NULL" : superType.getQualifiedName()));
                dg.addEdge(superType, type);

                /// HOLD UP: With interfaces, the only thing I want is that if the interface method has @EventSubscription, if the child doesn't also have that annotation on their method, throw an error.
                /// So its like two different, completely different things.
                /// Actually, nvm, I'm wrong about that. Interfaces with default methods do exist, so forced to include them.
                /// Include them, and only them.
                for(TypeMirror ifaceMirror : type.getInterfaces()) {
                    TypeElement iface = (TypeElement) typeUtils.asElement(ifaceMirror);
                    if (iface != null)
                        dg.addEdge(iface, type);
                }
            }

            List<TypeElement> rootElements = dg.getRoots();
            //dg.printGraph();
            System.out.println("RootElementCount: " + rootElements.size());
            HashMap<TypeElement,HashSet<MethodSignature>> classMethods = new HashMap<>();
            //for each class, see if they have methods annotated with EventSubscriber.
            //If they do, do your processing, and keep in mind what's going on.
            HashMap<TypeElement,HashSet<MethodSignature>> loggedErrors = new HashMap<>();
            //This setup means that by default, when the runtime program gets the methods, it's already computed the overrides.
            //This is now the place to be.
            for(TypeElement elem : rootElements){
                calc(dg, elem, classMethods,new HashSet<MethodSignature>(), loggedErrors,false,false);
            }

            //By this point, I'm just storing the methods in the correct place.
            //No error correction.
            HashMap<String,HashMap<String,HashSet<MethodSignature>>> finalClassMethods = new HashMap<>();
            for(TypeElement mClass : classMethods.keySet()){
                String className = toRuntimeClassName(mClass, processingEnv.getElementUtils());
                if(classMethods.get(mClass).isEmpty()) continue;
                HashMap<String,HashSet<MethodSignature>> myMethods = finalClassMethods.computeIfAbsent(className, k-> new HashMap<>());

                for(MethodSignature sig : classMethods.get(mClass)){
                    String eventType = sig.parameterTypes.getFirst();
                    HashSet<MethodSignature> sigStore = myMethods.computeIfAbsent(eventType,k-> new HashSet<>());
                    sigStore.add(sig);
                }
            }

            TPFEventFile eventFile = new TPFEventFile(finalClassMethods);
            writeEventFile(eventFile);

            return !finalClassMethods.isEmpty();
        }
        return false;
    }

    private void calc(DAG<TypeElement> dag, TypeElement root, HashMap<TypeElement,HashSet<MethodSignature>> classMethods, HashSet<MethodSignature> collectedMethods, HashMap<TypeElement,HashSet<MethodSignature>> loggedErrors, boolean haveSeenEventSubscription, boolean haveSeenMetaEventSubscription){
        List<? extends Element> enclosedElements = root.getEnclosedElements();
        HashSet<MethodSignature> privateMethods = new HashSet<>();
        boolean foundMetaEventSubcription = false;
        boolean foundEventSubcription = false;
        for(Element check : enclosedElements){
            if(!(check instanceof ExecutableElement method)) continue;
            if(method.getKind() != ElementKind.METHOD) continue;

            /// TODO: I need to include the MetaEventSubscription into this.

            EventSubscription eventAnnotation = method.getAnnotation(EventSubscription.class);
            MetaEventSubscription metaEventAnnotation = method.getAnnotation(MetaEventSubscription.class);
            /// TODO: I need to see if this current method will override anything that's in collectedMethods.
            /// If something is overriden, if you don't have the eventAnnotation annotation, throw an error.
            if(eventAnnotation == null && metaEventAnnotation == null){

                MethodSignature testSignature = generateTestMethodSignature(method);
                //This error was already logged, don't log it more than once.
                if(loggedErrors.containsKey(root) && loggedErrors.get(root).contains(testSignature)) continue;
                //You overrid an EventSubscriber method, but this method doesn't have the @EventSubscriber annotation
                //Reason that this type of error could happen more than once is if this class has multiple parents, its going to be gone over again.
                if(collectedMethods.contains(testSignature)){
                    MethodSignature originalSignature = null;
                    for(MethodSignature originalSigs : collectedMethods){
                        if(originalSigs.equals(testSignature)){
                            originalSignature = originalSigs;
                            break;
                        }
                    }

                    String subscriptionType = (originalSignature.isMetaEvent) ? "@MetaEventSubscription" : "@EventSubscription";
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"The class " + testSignature.originClass + " overrides the method " + testSignature.methodName + " of class " + originalSignature.originClass + ", but doesn't include the "+ subscriptionType +" annotation.", method);
                    loggedErrors.computeIfAbsent(root, k->new HashSet<>()).add(testSignature);
                }
                continue;
            }

            //if(isInterface && !method.isDefault()) continue;

            //Do the verification that this method is done right.
            //Since MethodSignature already has an override on its equals, adding anything to collectedMethods should override.
            /// TODO: Take the validation from the other method and put it here, null as placeholder
            boolean hasEventSubscription = method.getAnnotation(EventSubscription.class) != null;
            boolean hasMetaEventSubscription = method.getAnnotation(MetaEventSubscription.class) != null;

            MethodSignature validSignature = validateEventSubscriberMethod(method,hasEventSubscription,hasMetaEventSubscription);
            //Error occurred, stop
            if(validSignature == null) return;


            if(hasEventSubscription) foundEventSubcription = true;
            if(hasMetaEventSubscription) foundMetaEventSubcription = true;

            if(method.getModifiers().contains(Modifier.PRIVATE)){
                privateMethods.add(validSignature);
            }
            else
            {
                collectedMethods.add(validSignature);
            }
        }

        if(foundEventSubcription && foundMetaEventSubcription){
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"A class cannot have methods MetaEventSubscription and EventSubscription at the same time, but " + root.getQualifiedName() + " attempted to do so.",root);
            return;
        }


        if((foundEventSubcription || haveSeenEventSubscription) && (foundMetaEventSubcription || haveSeenMetaEventSubscription)){
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Conflict in " + root.getQualifiedName() + ": an ancestor defines " +
                            (haveSeenEventSubscription ? "@EventSubscription" : "@MetaEventSubscription") +
                            " methods, but this class defines " +
                            (foundEventSubcription ? "@EventSubscription" : "@MetaEventSubscription") +
                            " methods. Classes cannot mix both types across inheritance.",
                    root
            );
            return;
        }

        HashSet<MethodSignature> methodsOfClass = classMethods.computeIfAbsent(root, k->new HashSet<>());
        methodsOfClass.addAll(collectedMethods);
        methodsOfClass.addAll(privateMethods);

        Set<TypeElement> childs = dag.getChildren(root);
        for(TypeElement child : childs){
            calc(dag,child,classMethods,new HashSet<MethodSignature>(collectedMethods), loggedErrors, foundEventSubcription || haveSeenEventSubscription, foundMetaEventSubcription || haveSeenMetaEventSubscription);
        }
    }

    //This is the class that I need to modify for the meta events.
    //It'll follow the same logic, but I just need to make sure that whatever the event type is matches.
    private MethodSignature validateEventSubscriberMethod(ExecutableElement method, boolean hasEventSubscription, boolean hasMetaEventSubscription){
        if(method.getParameters().isEmpty()){
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"EventSubscription methods must include an event parameter, and optionally an adapter object", method);
            return null;
        }

        if(method.getParameters().size() > 2){
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"EventSubscription methods can only include an Event, and an EventAdapter", method);
            return null;
        }

        List<? extends VariableElement> parameters = method.getParameters();
        VariableElement firstParameter = parameters.getFirst();

        TypeMirror paramMirror = firstParameter.asType();
        if(paramMirror.getKind().isPrimitive()){
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "EventSubscription events cannot be of method primitive", firstParameter);
            return null;
        }

        if(checkIfIterable(paramMirror)){
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"EventSubscription events cannot be a Map, Array, nor other Iterable types", firstParameter);
            return null;
        }

        if(hasEventSubscription && hasMetaEventSubscription){
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"MetaEventSubscription and EventSubscription annotations are mutually exclusive", method);
            return null;
        }

        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        TypeMirror metaEventClass = elementUtils
                .getTypeElement("org.treepluginframework.meta_events.TPFMetaEvent")
                .asType();

        TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();

        if(hasMetaEventSubscription){



            if (enclosingClass.getAnnotation(TPFNode.class) != null) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Methods annotated with @MetaEventSubscription cannot be declared inside a @TPFNode class.",
                        method
                );
                return null;
            }

            TypeMirror erasedParam = typeUtils.erasure(paramMirror);
            TypeMirror erasedMetaEvent = typeUtils.erasure(metaEventClass);

            if (!typeUtils.isAssignable(erasedParam, erasedMetaEvent)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "MetaEventSubscription must have a class extending TPFMetaEvent as its type", firstParameter);
                return null;
            }
        }

        if(hasEventSubscription){
            if (enclosingClass.getAnnotation(TPFMetaEventListener.class) != null) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Methods annotated with @EventSubscription cannot be declared inside a @TPFMetaEventListener class.",
                        method
                );
                return null;
            }

            if(typeUtils.isAssignable(paramMirror,metaEventClass))
            {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"EventSubscription cannot have a class extending TPFMetaEvent as its type", firstParameter);
                return null;
            }
        }

        if(parameters.size() == 2){
            VariableElement secondParameter = parameters.get(1);
            boolean errorOccurred = handleEventAdapter(firstParameter,secondParameter);
            if(!errorOccurred){
                return null;
            }
        }

        List<String> paramTypeNames = new ArrayList<>();

        for (VariableElement param : parameters) {
            TypeMirror paramType = param.asType();
            if (paramType.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredType = (DeclaredType) paramType;
                TypeElement typeElement = (TypeElement) declaredType.asElement();

                paramTypeNames.add(toRuntimeClassName(typeElement, processingEnv.getElementUtils()));
                //paramTypeNames.add(typeElement.getQualifiedName().toString());
            } else {
                // Handle primitives and arrays.
                //Should run anymore, problem if it does happen.
                paramTypeNames.add(paramType.toString());
            }
        }

        DeclaredType declaredType = (DeclaredType) paramMirror;
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        //String enclosingClassName = ((TypeElement)method.getEnclosingElement()).getQualifiedName().toString();
        String enclosingClassName = toRuntimeClassName((TypeElement) method.getEnclosingElement(), processingEnv.getElementUtils());
        System.out.println("Enclosing Class Name: " + enclosingClassName);
        //HashMap<String,HashSet<MethodSignature>> classMethods = savedMethods.computeIfAbsent(enclosingClassName,k -> new HashMap<>());
        //HashSet<MethodSignature> methodsOfType = classMethods.computeIfAbsent(qualifiedName, k -> new HashSet<>());
        boolean isAbstract = typeElement.getModifiers().contains(Modifier.ABSTRACT);

        boolean isInterface = typeElement.getKind().isInterface();
        //An interface method(not default) or an abstract method won't get marked as an implementation.
        boolean notImplemented = (isInterface && !method.getModifiers().contains(Modifier.DEFAULT)) || method.getModifiers().contains(Modifier.ABSTRACT);
        int priority = (hasEventSubscription) ? method.getAnnotation(EventSubscription.class).priority() : method.getAnnotation(MetaEventSubscription.class).priority();
        boolean useSubClasses = (hasEventSubscription) ? method.getAnnotation(EventSubscription.class).useSubClasses() : method.getAnnotation(MetaEventSubscription.class).useSubClasses();

        MethodSignature sig = new MethodSignature(enclosingClassName,method.getSimpleName().toString(), paramTypeNames, priority, parameters.size() == 2, isAbstract || useSubClasses, method.getModifiers().contains(Modifier.PRIVATE), notImplemented, hasMetaEventSubscription);
        //methodsOfType.add(sig);
        return sig;
    }


    private MethodSignature generateTestMethodSignature(ExecutableElement method){
        String enclosingClassName = toRuntimeClassName((TypeElement) method.getEnclosingElement(), processingEnv.getElementUtils());
        List<String> paramTypeNames = new ArrayList<>();

        for (VariableElement param : method.getParameters()) {
            TypeMirror paramType = param.asType();
            if (paramType.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredType = (DeclaredType) paramType;
                TypeElement typeElement = (TypeElement) declaredType.asElement();

                paramTypeNames.add(toRuntimeClassName(typeElement, processingEnv.getElementUtils()));
                //paramTypeNames.add(typeElement.getQualifiedName().toString());
            } else {
                // Handle primitives and arrays.
                //Should run anymore, problem if it does happen.
                paramTypeNames.add(paramType.toString());
            }
        }

        return new MethodSignature(enclosingClassName, method.getSimpleName().toString(), paramTypeNames, -1,false,false,false, false, false);
    }

    /*
    /// TODO: Make it so that if a class has the same event multiple times, you are forced to put a priority.
    /// TODO: Need to do a type hierarchy walk.
    ///
    /// TODO: Walk through the interfaces and abstract classes that have the methods first. Then, when that's done, make sure that whatever overriding method is present also has to have the EventSubscription annotation, otherwise throw an error.
    ///
    /// Probably force interfaces to have priorities too.
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if(true) return processV2(annotations,roundEnv);

        Set<? extends Element> eventAnnotations = roundEnv.getElementsAnnotatedWith(EventSubscription.class);
        if(eventAnnotations.isEmpty()) return false;



        System.out.println("Dealing with the events");
        //The Class that contains the methods.
        //The event type
        //The Methods that have that event type.
        Map<String,HashMap<String, HashSet<MethodSignature>>> savedMethods = new HashMap<>();

        //I need to compile a list of classes that have the eventSubscriber annotation.
        //Then with that list, I need to traverse their type hierarchies to see inheritance methods.
        //Any annotations that are processed of these abstract classes/interfaces need to be marked as not needing to be processed afterwards.
        //I essentially need to keep track of the method signature, and see if it matches another method.
        //I need to only inherit public, protected, and package-private methods.
        //Any private methods are a no-go, so don't inherit them.
        //Actually, its weird. Don't let them be overriden, but they still need to be part of the child class. So I'll need to know where the method came from.
        //So I need to decide a direction to move in. I either start from the abstract classes, and work my way up to any subclasses that are present(is that even possible?).
        //Or I start with the subclasses, and then move onto the abstract/lower level ones. This has the problem that I don't know the subclasses, since they themselves may not have the annotation.
        /***
         * Step 1 — Compile-time (annotation processor)
         *
         * Goal: Process only the classes you can see in the current compilation round.
         *
         * Tasks:
         *
         * Scan all classes annotated with @EventSubscriber.
         *
         * For each annotated class, traverse its superclass chain.
         *
         * Collect all methods with @EventSubscriber.
         *
         * Respect visibility rules: only public, protected, package-private (ignore private for inheritance).
         *
         * Keep track of method origin (which class actually declared it).
         *
         * Generate metadata:
         *
         * Annotated class → list of subscriber methods (including inherited ones).
         *
         * You don’t need to care about subclasses yet — just handle what exists.
         *
         * Result: A map of “annotated class → subscriber methods” that can be shipped to runtime.
         *
         * Step 2 — Runtime
         *
         * Goal: Apply the compiled metadata to all actual instances, including subclasses that weren’t annotated.
         *
         * Tasks:
         *
         * For each annotated class you processed at compile-time, find all subclasses in the runtime environment.
         *
         * Reflection can help here (Reflections library, or your own classpath scanning).
         *
         * Includes concrete classes like B in your example, which don’t have @EventSubscriber.
         *
         * Register the subscriber methods from the superclass onto each subclass instance.
         *
         * They’re already tracked with origin info, so you know which class declared the method.
         *
         * Fire events normally — any instance of a subclass will now invoke the inherited subscriber methods correctly.

        
        HashSet<TypeElement> abstractClasses = new HashSet<>();
        HashSet<TypeElement> interfaceClasses = new HashSet<>();

        //So I've stored the abstract classes, and the interface Classes.
        //After I do the processing, I need to walk up the type hierarchy of the abstract classes.
        for(Element m : eventAnnotations){
            ExecutableElement method = (ExecutableElement) m;
            TypeElement classWithMethod = (TypeElement) method.getEnclosingElement();
            if(classWithMethod.getModifiers().contains(Modifier.ABSTRACT)){
                abstractClasses.add(classWithMethod);
            }
            else if(classWithMethod.getKind().isInterface()){
                interfaceClasses.add(classWithMethod);
            }
        }
        //So I guess compute all of this, and then once all the saved methods are done, do a type hierarchy walk.


        //Any classes afterwards will need to walk up their type hierarchies of superclass.
        //This is only true for abstract classes and not interfaces, since any interface method not implemented by the abstract class would have to be implemented by the concrete class.
        //So I just walk up the superclasses, and interfaces aren't a problem.

        for(Element m : eventAnnotations){
           ExecutableElement method = (ExecutableElement)m;
           if(method.getParameters().isEmpty()){
               processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"EventSubscription methods must include an event parameter, and optionally an adapter object", method);
               continue;
           }

           if(method.getParameters().size() > 2){
               processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"EventSubscription methods can only include an Event, and an EventAdapter", method);
               continue;
           }

            List<? extends VariableElement> parameters = method.getParameters();
            VariableElement firstParameter = parameters.getFirst();

            TypeMirror paramMirror = firstParameter.asType();
            if(paramMirror.getKind().isPrimitive()){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "EventSubscription events cannot be of method primitive", firstParameter);
                continue;
            }

            if(checkIfIterable(paramMirror)){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"EventSubscription events cannot be a Map, Array, nor other Iterable types", firstParameter);
                continue;
            }

            if(parameters.size() == 2){
                VariableElement secondParameter = parameters.get(1);
                boolean errorOccurred = handleEventAdapter(firstParameter,secondParameter);
                if(!errorOccurred){
                    continue;
                }
            }

            List<String> paramTypeNames = new ArrayList<>();

            for (VariableElement param : parameters) {
                TypeMirror paramType = param.asType();
                if (paramType.getKind() == TypeKind.DECLARED) {
                    DeclaredType declaredType = (DeclaredType) paramType;
                    TypeElement typeElement = (TypeElement) declaredType.asElement();
                    paramTypeNames.add(typeElement.getQualifiedName().toString());
                } else {
                    // Handle primitives and arrays
                    paramTypeNames.add(paramType.toString());
                }
            }

            DeclaredType declaredType = (DeclaredType) paramMirror;
            TypeElement typeElement = (TypeElement) declaredType.asElement();
            String qualifiedName = typeElement.getQualifiedName().toString();

            //String enclosingClassName = ((TypeElement)method.getEnclosingElement()).getQualifiedName().toString();
            String enclosingClassName = toRuntimeClassName((TypeElement) method.getEnclosingElement(), processingEnv.getElementUtils());

            HashMap<String,HashSet<MethodSignature>> classMethods = savedMethods.computeIfAbsent(enclosingClassName,k -> new HashMap<>());
            HashSet<MethodSignature> methodsOfType = classMethods.computeIfAbsent(qualifiedName, k -> new HashSet<>());
            boolean isAbstract = typeElement.getModifiers().contains(Modifier.ABSTRACT);
            EventSubscription eS = method.getAnnotation(EventSubscription.class);
            MethodSignature sig = new MethodSignature("",method.getSimpleName().toString(), paramTypeNames, eS.priority(), parameters.size() == 2, isAbstract || eS.useSubClasses(), false,false);
            methodsOfType.add(sig);
        }

        TPFEventFile eventFile = new TPFEventFile(savedMethods);
        writeEventFile(eventFile);
        return true;
    }
     */

    private String toRuntimeClassName(TypeElement typeElement, Elements elementUtils) {
        String packageName = elementUtils.getPackageOf(typeElement).getQualifiedName().toString();
        String fullQualifiedName = typeElement.getQualifiedName().toString();

        if (packageName.isEmpty()) {
            // Default package, rare case
            return fullQualifiedName.replace('.', '$');
        }

        // Get class path portion (relative to package)
        String classPath = fullQualifiedName.substring(packageName.length() + 1); // +1 to skip dot
        classPath = classPath.replace('.', '$'); // Replace inner class dots with $

        return packageName + "." + classPath;
    }

    private void writeEventFile(TPFEventFile metaFile){
        try {
            // Create resource file under META-INF/tpf/
            FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/tpf/event.json");
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


    /***
     *
     * @param eventParameter
     * @param adapterParameter
     * @return Returns true if there was no error, returns false if there was an error
     */
    private boolean handleEventAdapter(VariableElement eventParameter, VariableElement adapterParameter)
    {
        TypeMirror eventTypeMirror = eventParameter.asType();
        TypeMirror adapterTypeMirror = adapterParameter.asType();
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        TypeMirror adapterClassMirror = elementUtils
                .getTypeElement("org.treepluginframework.events.EventAdapter")
                .asType();

        TypeMirror erasedBaseType = typeUtils.erasure(adapterClassMirror);
        TypeMirror erasedAdapterType = typeUtils.erasure(adapterTypeMirror);

        boolean isAssignable = typeUtils.isAssignable(erasedAdapterType, erasedBaseType);

        if(!isAssignable)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"The second parameter must be of EventAdapter type",adapterParameter);
            return false;
        }

        DeclaredType adapterDeclaredType = (DeclaredType) adapterParameter.asType();
        TypeMirror genericEventType = getGenericParameterFromAdapter(adapterDeclaredType, adapterClassMirror);
        if(genericEventType == null)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"Invalid EventAdapter, potentially not a descendant from EventAdapter class",adapterParameter);
            return false;
        }

        //Okay, now that I have the generic, this is what I have to do.
        //I need to see if the event is assignable from the generic type. If it's not, type missmatch.

        boolean adapterEventCompatible = typeUtils.isAssignable(eventTypeMirror,genericEventType);
        if(!adapterEventCompatible)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"Event of type " + eventTypeMirror.toString() + " is not assignable to the event's adapters expected event type, " + genericEventType.toString(), eventParameter);
            return false;
        }

        return true;
    }

    private TypeMirror getGenericParameterFromAdapter(DeclaredType adapterType, TypeMirror targetBaseMirror) {
        Types typeUtils = processingEnv.getTypeUtils();

        DeclaredType current = adapterType;

        while (true) {
            TypeElement currentElement = (TypeElement) current.asElement();
            TypeMirror erasedCurrent = typeUtils.erasure(current);
            TypeMirror erasedTarget = typeUtils.erasure(targetBaseMirror);

            if (typeUtils.isSameType(erasedCurrent, erasedTarget)) {
                List<? extends TypeMirror> typeArguments = current.getTypeArguments();
                if (!typeArguments.isEmpty()) {
                    return typeArguments.get(0);
                }
            }

            TypeMirror superclass = currentElement.getSuperclass();
            if (!(superclass instanceof DeclaredType superclassDeclared)) {
                break;
            }

            current = superclassDeclared;
        }

        return null;
    }

    private boolean checkIfIterable(TypeMirror mirror){
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        TypeMirror iterableType = elementUtils.getTypeElement("java.lang.Iterable").asType();
        TypeMirror mapType = elementUtils.getTypeElement("java.util.Map").asType();

        boolean isIterable = typeUtils.isAssignable(mirror, typeUtils.erasure(iterableType));
        boolean isMap = typeUtils.isAssignable(mirror, typeUtils.erasure(mapType));

        return mirror.getKind() == TypeKind.ARRAY || isIterable || isMap;
    }
}
