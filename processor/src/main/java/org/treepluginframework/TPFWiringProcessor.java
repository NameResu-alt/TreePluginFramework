package org.treepluginframework;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class TPFWiringProcessor extends AbstractProcessor {

    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        //org.treepluginframework.EventSubscription
        return Set.of("org.treepluginframework.TPFAutoWireChild","ord.treepluginframework.TPFNode");
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> nodeElements = roundEnv.getElementsAnnotatedWith(TPFNode.class);

        if(!nodeElements.isEmpty())
        {
            try {
                FileObject file = filer.createResource(
                        StandardLocation.CLASS_OUTPUT, "", "META-INF/tpf-context/auto-node"
                );
                try (Writer writer = file.openWriter()) {
                    for (Element element : nodeElements) {

                        TypeElement typeElement = (TypeElement) element;

                        boolean hasNoConstructor = true;
                        boolean hasOnlyNoArgsConstructor = true;

                        Element problem = null;
                        for (Element enclosed : typeElement.getEnclosedElements()) {
                            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                                hasNoConstructor = false;
                                ExecutableElement constructor = (ExecutableElement) enclosed;
                                if (!constructor.getParameters().isEmpty()) {
                                    hasOnlyNoArgsConstructor = false;
                                    problem = enclosed;
                                    break;
                                }
                            }
                        }

                        if (!hasNoConstructor && !hasOnlyNoArgsConstructor) {
                            error("Class annotated with @TPFNode must have either no constructor or only a no-args constructor.",problem);
                            continue;
                        }

                        // Safe to use
                        String className = typeElement.getQualifiedName().toString();
                        writer.write(className + "\n");
                    }
                }
                System.out.println("Writing auto-node file to: " + file.toUri());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Set<? extends Element> wireElements = roundEnv.getElementsAnnotatedWith(TPFAutoWireChild.class);
        if(!wireElements.isEmpty()){
            try{
                FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "","META-INF/tpf-context/auto-child-wires");
                HashMap<String,String> preventDuplicates = new HashMap<>();
                Set<String> parents = new HashSet<>();
                try(Writer writer = file.openWriter())
                {
                    for(Element element : wireElements)
                    {
                        //Guaranteed that this is a field, not a method.
                        VariableElement fieldElement = (VariableElement)element;
                        TypeMirror fieldType = fieldElement.asType();

                        Element fieldTypeElement = processingEnv.getTypeUtils().asElement(fieldType);

                        if(fieldTypeElement == null || !(fieldTypeElement instanceof TypeElement))
                        {
                            error("Could not resolve type of @TPFAutoWireChild field, may be a primitive",element);
                            continue;
                        }

                        TypeElement fieldTypeTypeElement = (TypeElement) fieldTypeElement;
                        if(fieldTypeTypeElement.getAnnotation(TPFNode.class) == null)
                        {
                            error("Field type " + fieldTypeTypeElement.getQualifiedName() + " must be annoted with @TPFNode to be injected",element);
                            continue;
                        }



                        // If it's valid, write the class name that declares the field
                        Element enclosing = fieldElement.getEnclosingElement();
                        TypeElement enclosingType = (TypeElement) fieldElement.getEnclosingElement();

                        if (processingEnv.getTypeUtils().isSameType(enclosingType.asType(), fieldTypeTypeElement.asType())) {
                            error("Class " + enclosingType.getQualifiedName() + " cannot @TPFAutoWireChild itself.", element);
                            continue;
                        }

                        String className = ((TypeElement) enclosing).getQualifiedName().toString();
                        //WireTarget is the type
                        String wireTarget = ((TypeElement) fieldTypeElement).getQualifiedName().toString();
                        if(preventDuplicates.containsKey(wireTarget))
                        {
                            error("The wire target " + wireTarget + " has already been claimed by another class, " + preventDuplicates.get(wireTarget) + ", Ignoring " + className,element);
                            continue;
                        }

                        /*
                        if(parents.contains(wireTarget))
                        {
                            error("The class " + wireTarget + " is already the parent of another node",element);
                            continue;
                        }
                        */

                        parents.add(className);


                        preventDuplicates.put(wireTarget,className);
                        writer.write(className + "," + fieldElement.getSimpleName().toString()+ ","+wireTarget + "\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }


    private void error(String msg, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
    }
}
