package org.treepluginframework.EventSystem.ComponentArchitecture;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Set;


@SupportedAnnotationTypes("org.imperium.EventSystem.ComponentArchitecture.EventSubscription")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class EventSubscriberProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for(Element element : roundEnv.getElementsAnnotatedWith(EventSubscription.class))
        {
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
                continue;
            }


            if(params.size() == 2){
                handleEventAdapter(params.get(0),params.get(1));
            }


            //2 things here.
            //A: Make sure that the second parameter is an EventAdapter
            //B: Make sure that whatever event type the adapter wants is compatible with the event itself.

            /*
                The Event can be anything, so I can't check against the Event class.
                Instead, I need to make sure that the first parameter is an object
                If it's not, then raise a problem.

                The second parameter is the EventAdapter. That one I can type check.
             */

        }


        return true;
    }

    private boolean handleEventAdapter(VariableElement eventParameter, VariableElement adapterParameter)
    {
        TypeMirror eventType = eventParameter.asType();
        TypeMirror adapterType = adapterParameter.asType();

        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        TypeElement adapterElement = elementUtils.getTypeElement(EventAdapter.class.getName());

        if (adapterElement == null) {
            error("Could not find EventAdapter class.", adapterParameter);
            return true;
        }

        // Ensure adapter is a subtype of EventAdapter
        if (!typeUtils.isAssignable(adapterType, adapterElement.asType())) {
            error("Second parameter must be an EventAdapter.", adapterParameter);
            return true;
        }

        // Check if adapterType is a DeclaredType with a type argument
        if (adapterType instanceof DeclaredType declaredType) {
            List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
            if (typeArgs.size() == 1) {
                TypeMirror adapterGenericType = typeArgs.get(0);

                // Check if eventType is assignable to adapterGenericType
                if (!typeUtils.isAssignable(eventType, adapterGenericType)) {
                    error("First parameter must be assignable to the generic type of the EventAdapter.", eventParameter);
                    return true;
                }
            } else {
                error("EventAdapter must have exactly one generic type argument.", adapterParameter);
                return true;
            }
        } else {
            error("Expected a parameterized type for EventAdapter.", adapterParameter);
            return true;
        }

        return false;
    }


    /***
     *
     * @param firstParameter
     * @return Returns true if should continue past, since got an error.
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
