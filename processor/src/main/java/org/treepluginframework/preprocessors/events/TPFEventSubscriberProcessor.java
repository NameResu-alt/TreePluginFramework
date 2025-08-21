package org.treepluginframework.preprocessors.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import com.sun.source.tree.Tree;
import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.values.MethodSignature;
import org.treepluginframework.values.TPFEventFile;
import org.treepluginframework.values.TPFMetadataFile;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
        return Set.of("org.treepluginframework.annotations.EventSubscription");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> eventAnnotations = roundEnv.getElementsAnnotatedWith(EventSubscription.class);
        if(eventAnnotations.isEmpty()) return false;

        System.out.println("Dealing with the events");
        Map<String,HashMap<String,MethodSignature>> savedMethods = new HashMap<>();

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

            HashMap<String,MethodSignature> classMethods = savedMethods.computeIfAbsent(enclosingClassName,k -> new HashMap<>());


            EventSubscription eS = method.getAnnotation(EventSubscription.class);
            classMethods.put(qualifiedName, new MethodSignature(method.getSimpleName().toString(), paramTypeNames, eS.priority(), parameters.size() == 2));
        }

        TPFEventFile eventFile = new TPFEventFile(savedMethods);
        writeEventFile(eventFile);
        return true;
    }

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
}
