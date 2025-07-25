package org.treepluginframework.preprocessors.events;

import com.google.auto.service.AutoService;
import org.treepluginframework.annotations.EventSubscription;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

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
        System.out.println("Are you doing this at all?!");
        if(eventAnnotations.isEmpty()) return false;

        return true;
    }
}
