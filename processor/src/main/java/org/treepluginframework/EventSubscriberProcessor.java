package org.treepluginframework;

import com.google.auto.service.AutoService;
import org.treepluginframework.EventSubscription;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Set;


/*
@SupportedAnnotationTypes("org.imperium.EventSystem.ComponentArchitecture.EventSubscription")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
*/
@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class EventSubscriberProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        //org.treepluginframework.EventSubscription
        return Set.of("org.treepluginframework.EventSubscription");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        System.out.println("Processor running!");
        for(Element element : roundEnv.getElementsAnnotatedWith(EventSubscription.class))
        {
            System.out.println("Got inside! " + element.getSimpleName());
            if(element.getKind() != ElementKind.METHOD){
                error("Only methods can be annotated with @EventSubscriber", element);
                continue;
            }

            ExecutableElement method = (ExecutableElement) element;
            List<? extends VariableElement> params = method.getParameters();

            if (params.isEmpty() || params.size() > 2) {
                error("Method must have either 1 or 2 parameters. Event, or Event and Adapter", element);
                continue;
            }

            boolean shouldContinue = handleEventParameter(params.get(0));

            if(shouldContinue)
            {
                //That means that an error occured.
                continue;
            }


            if(params.size() == 2){
                handleEventAdapter(params.get(0),params.get(1));
            }
        }

        return true;
    }

    private void handleEventAdapter(VariableElement eventParameter, VariableElement adapterParameter)
    {
        TypeMirror eventTypeMirror = eventParameter.asType();
        TypeMirror adapterTypeMirror = adapterParameter.asType();
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        TypeMirror adapterClassMirror = elementUtils
                .getTypeElement("org.treepluginframework.EventSystem.ComponentArchitecture.EventAdapter")
                .asType();

        TypeMirror erasedBaseType = typeUtils.erasure(adapterClassMirror);
        TypeMirror erasedAdapterType = typeUtils.erasure(adapterTypeMirror);

        boolean isAssignable = typeUtils.isAssignable(erasedAdapterType, erasedBaseType);

        if(!isAssignable)
        {
            error("The second parameter must be of EventAdapter type",adapterParameter);
            return;
        }

        /*
        String checking = "Checking: " + typeUtils.isAssignable(adapterTypeMirror, adapterClassMirror);
        checking += "\n"+adapterTypeMirror.toString();
         */

        DeclaredType adapterDeclaredType = (DeclaredType) adapterParameter.asType();
        TypeElement adapterTypeElement = (TypeElement) adapterDeclaredType.asElement();

        TypeMirror genericEventType = getGenericParameterFromAdapter(adapterTypeElement, adapterClassMirror);

        if(genericEventType == null)
        {
            error("Invalid EventAdapter, potentially not a descendant from EventAdapter class",adapterParameter);
            return;
        }

        //Okay, now that I have the generic, this is what I have to do.
        //I need to see if the event is assignable from the generic type. If it's not, type missmatch.


        boolean adapterEventCompatible = typeUtils.isAssignable(eventTypeMirror,genericEventType);

        if(!adapterEventCompatible)
        {
            error("Event of type " + eventTypeMirror.toString() + " is not assignable to the event's adapters expected event type, " + genericEventType.toString(), eventParameter);
            return;
        }

        //String checking = "Type of generic: " + genericEventType.toString() + " " + maybe;
    }


    private TypeMirror getGenericParameterFromAdapter(TypeElement subclassElement, TypeMirror targetBaseMirror) {
        Types typeUtils = processingEnv.getTypeUtils();

        TypeMirror current = subclassElement.asType();

        while (current instanceof DeclaredType declared) {
            TypeMirror erasedCurrent = typeUtils.erasure(current);
            TypeMirror erasedTarget = typeUtils.erasure(targetBaseMirror);

            if (typeUtils.isSameType(erasedCurrent, erasedTarget)) {
                // We found EventAdapter<X>
                List<? extends TypeMirror> typeArguments = declared.getTypeArguments();
                if (!typeArguments.isEmpty()) {
                    return typeArguments.get(0);
                }
            }

            Element currentElement = declared.asElement();
            if (!(currentElement instanceof TypeElement currentTypeElement)) break;

            current = currentTypeElement.getSuperclass();
        }

        return null; // Not found
    }


    /***
     *
     * @param firstParameter
     * @return Returns true if should continue past, since got an error. Makes sure that whatever comes first is probably some type of object.
     */
    private boolean handleEventParameter(VariableElement firstParameter){

        TypeMirror firstParam = firstParameter.asType();

        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        if(firstParam.getKind().isPrimitive())
        {
            error("First parameter must NOT be a primitive", firstParameter);
            return true;
        }


        //Realistically, the only thing I have to check against are primitives, and making sure that first parameter isn't an EventAdapter Itself
        //Like I mean, I can allow an EventAdapter of an EventAdapter, but that's just kinda rough.

        // Check String
        TypeElement stringElement = elementUtils.getTypeElement("java.lang.String");
        if (stringElement != null) {
            TypeMirror stringType = stringElement.asType();
            if (typeUtils.isSameType(firstParam, stringType)) {
                error("First parameter must NOT be a String", firstParameter);
                return true;
            }
        }

        // Check Iterable (implements interface java.lang.Iterable)
        TypeElement iterableElement = elementUtils.getTypeElement("java.lang.Iterable");
        if (iterableElement != null && typeUtils.isAssignable(firstParam, iterableElement.asType())) {
            error("First parameter must NOT be Iterable", firstParameter);
            return true;
        }

        // Check Map (implements interface java.util.Map)
        TypeElement mapElement = elementUtils.getTypeElement("java.util.Map");
        if (mapElement != null && typeUtils.isAssignable(firstParam, mapElement.asType())) {
            error("First parameter must NOT be a Map", firstParameter);
            return true;
        }

        return false;
    }

    private void error(String msg, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
    }
}
