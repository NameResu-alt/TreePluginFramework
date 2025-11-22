package org.treepluginframework.preprocessors.values;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import org.treepluginframework.annotations.TPFValue;
import org.treepluginframework.component_architecture.DAG;
import org.treepluginframework.preprocessors.Utilities;
import org.treepluginframework.values.ClassValueMetadata;
import org.treepluginframework.values.TPFValueFile;
import org.treepluginframework.values.VariableValueInfo;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.util.*;

@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class TPFValueProcessor extends AbstractProcessor {
    private Filer filer;
    Set<TypeElement> allTypes = new HashSet<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes(){
        return Set.of("org.treepluginframework.annotations.TPFValue");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //I need to get all RootElements, since some of them may not carry the value, but their parents did
        for(Element m : roundEnv.getRootElements()){
            if(m instanceof TypeElement type){
                allTypes.add(type);
            }
        }

        //I need to get the classes annotated with TPFValue, this covers inner classes
        for(Element m : roundEnv.getElementsAnnotatedWith(TPFValue.class)){
            if(m.getEnclosingElement() instanceof TypeElement holdingClass){
                allTypes.add(holdingClass);
            }
        }

        //Constructor annotations aren't inheritted, need to remember that.
        //Its just fields that can be inherited.
        //Also, its a case where even private fields need to be inheritted, since its technically still of the class.
        if(roundEnv.processingOver()){

            /// NOTE: Before I even begin to do this, I need to read resource files to figure out
            /// What fields/constructors other projects had.
            /// This allows the dependency to transcend a single project.
            /// So project A -> project B -> project C would work.
            /// So read all the potential value.json META-INF files that other jars could have.

            TPFValueFile mergedFile = findPreviousValueFiles();


            DAG<TypeElement> dag = new DAG<>();//DAG.regular();//new DAG<>();
            Types typeUtils = processingEnv.getTypeUtils();
            for(TypeElement type : allTypes){

                if(type.getKind() == ElementKind.INTERFACE)
                {
                    //Interfaces don't have fields that can be value injected anyways
                    continue;
                }

                TypeMirror superMirror = type.getSuperclass();
                TypeElement superType = null;
                if(superMirror.getKind() != TypeKind.NONE){
                    superType = (TypeElement) typeUtils.asElement(superMirror);
                }

                //Not including Object into it.
                if (superType != null && superType.getQualifiedName().contentEquals("java.lang.Object")) {
                    superType = null;
                }

                dag.addEdge(superType, type);
            }

            dag.printGraph();

            //Class, originClass, fieldname, fieldInfo.
            HashMap<FieldKey, VariableValueInfo> classFields = new HashMap<>();
            //Class, Constructor Signature, Position in Constructor, FieldInfo
            HashMap<ParameterKey, VariableValueInfo> classConstructors = new HashMap<>();

            List<TypeElement> roots = dag.getRoots();
            for(TypeElement root : roots){
                System.out.println("Root: " + Utilities.toRuntimeClassName(root, processingEnv.getElementUtils()));
                calc(root, dag,classFields,classConstructors,new HashSet<>());
            }


            HashSet<String> globalValueLocations = new HashSet<>();
            HashMap<String,HashSet<String>> configValueLocations = new HashMap<>();

            HashMap<String, ClassValueMetadata> classData = new HashMap<>();

            System.out.println("Keys: " + classFields.keySet().toString());
            for(FieldKey key : classFields.keySet())
            {
                String className = key.className;
                String originClass = key.originClass;
                String fieldName = key.fieldName;

                ClassValueMetadata data = classData.computeIfAbsent(className, k->new ClassValueMetadata());
                HashMap<String, VariableValueInfo> fields = data.fields.computeIfAbsent(originClass, k-> new HashMap<>());

                VariableValueInfo info = classFields.get(key);
                if(info.fileName.isBlank()){
                    globalValueLocations.add(info.location);
                }
                else
                {
                    configValueLocations.computeIfAbsent(info.fileName,k->new HashSet<>()).add(info.location);
                }

                fields.put(fieldName,info);
            }

            for(ParameterKey key : classConstructors.keySet())
            {
                String className = key.className;
                String signature = key.constructorSignature;
                int positionInConstructor = key.positionInConstructor;

                ClassValueMetadata data = classData.computeIfAbsent(className, k->new ClassValueMetadata());
                HashMap<Integer, VariableValueInfo> constructor = data.constructors.computeIfAbsent(signature, k -> new HashMap<>());

                VariableValueInfo info = classConstructors.get(key);
                if(info.fileName.isBlank()){
                    globalValueLocations.add(info.location);
                }
                else
                {
                    configValueLocations.computeIfAbsent(info.fileName,k->new HashSet<>()).add(info.location);
                }
                constructor.put(positionInConstructor, info);
            }

            TPFValueFile valueFile = new TPFValueFile(classData,globalValueLocations,configValueLocations);
            writeValueFile(valueFile);

            return true;
        }
        return false;
    }

    private void calc(TypeElement type, DAG<TypeElement> dag, HashMap<FieldKey, VariableValueInfo> classFields, HashMap<ParameterKey, VariableValueInfo> classConstructors , HashSet<VariableValueInfo> inheritedClassFields){

        String className = Utilities.toRuntimeClassName(type, processingEnv.getElementUtils());

        List<? extends Element> enclosed = type.getEnclosedElements();
        for(Element e : enclosed){
            //Its the constructors that are a problem.
            if(e.getKind() == ElementKind.CONSTRUCTOR)
            {
                System.out.println("Found Constructor");
                ExecutableElement constructor = (ExecutableElement) e;
                String constructorSignature = Utilities.createConstructorSignature(constructor);
                List<? extends VariableElement> parameters = constructor.getParameters();

                for(int i = 0; i<parameters.size();i++){
                    VariableElement parameter = parameters.get(i);

                    TPFValue valueAnnotation = parameter.getAnnotation(TPFValue.class);
                    if(valueAnnotation == null) continue;;

                    if(valueAnnotation.location().isEmpty() || valueAnnotation.location().isBlank()){
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "TPFValue annotations must provide a location", parameter);
                        continue;
                    }

                    TypeMirror paramType = parameter.asType();

                    String parameterType = Utilities.getQualifiedTypeName(paramType, processingEnv.getTypeUtils());

                    ParameterKey key = new ParameterKey(className, constructorSignature,i);
                    classConstructors.put(key,new VariableValueInfo(className,"N/A",parameterType, valueAnnotation.fileName(), valueAnnotation.location(),valueAnnotation.defaultValue(),false));
                }

                continue;
            }
            else if(!(e instanceof VariableElement)){
                continue;
            }

            VariableElement variable = (VariableElement) e;

            TPFValue valueAnnotation = variable.getAnnotation(TPFValue.class);
            if(valueAnnotation == null) {
                continue;
            }

            if(variable.getModifiers().contains(Modifier.FINAL)){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"Cannot have TPFValue on fields that are final");
                continue;
            }

            if(valueAnnotation.location().isEmpty() || valueAnnotation.location().isBlank()){
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "TPFValue annotations must provide a location", variable);
                continue;
            }


            TypeMirror varType = variable.asType();

            String variableType = Utilities.getQualifiedTypeName(varType, processingEnv.getTypeUtils());

            VariableValueInfo newInfo = new VariableValueInfo(className, variable.getSimpleName().toString(), variableType,valueAnnotation.fileName(),valueAnnotation.location(),valueAnnotation.defaultValue(),variable.getModifiers().contains(Modifier.PRIVATE));
            inheritedClassFields.remove(newInfo);
            inheritedClassFields.add(newInfo);
        }

        if(!inheritedClassFields.isEmpty()){
            System.out.println("Current Class: " + className);
            for(VariableValueInfo v : inheritedClassFields){
                FieldKey key = new FieldKey(className,v.originClass,v.fieldName);
                System.out.println("\tKey: " + key);
                classFields.put(key,v);
            }
        }

        Set<TypeElement> children = dag.getChildren(type);
        for(TypeElement child : children){
            calc(child, dag,classFields, classConstructors,new HashSet<>(inheritedClassFields));
        }
    }

    private void writeValueFile(TPFValueFile metaFile){
        try {
            // Create resource file under META-INF/tpf/
            FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/tpf/value.json");
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

    private TPFValueFile findPreviousValueFiles(){
        TPFValueFile mergedFile = new TPFValueFile();
        ObjectMapper mapper = new ObjectMapper();

        try {
            ClassLoader cl = this.getClass().getClassLoader();
            Enumeration<URL> resources = cl.getResources("META-INF/tpf/value.json");

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream in = url.openStream()) {
                    TPFValueFile valueFile = mapper.readValue(in, TPFValueFile.class);
                    mergedFile.merge(valueFile);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error handling META-INF/value.json resources", e);
        }

        return mergedFile;
    }

    record FieldKey(String className, String originClass, String fieldName) {}
    record ParameterKey(String className, String constructorSignature, int positionInConstructor){}
}
