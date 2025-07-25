package org.treepluginframework.component_architecture;

import org.treepluginframework.values.ClassValueMetadata;
import org.treepluginframework.values.ConstructorInformation;
import org.treepluginframework.values.ParameterValueInfo;
import org.treepluginframework.values.TPFMetadataFile;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class TPFNodeRepository {
    //I need to store the nodes that I got in here.
    //Just putting them into the dispatcher doesn't seem like a sound decision.
    //I'm assuming that there will only ever be 1 type of node, maybe better way?
    private static final Map<String, Class<?>> PRIMITIVE_TYPE_MAP = Map.ofEntries(
            Map.entry("boolean", boolean.class),
            Map.entry("byte", byte.class),
            Map.entry("char", char.class),
            Map.entry("short", short.class),
            Map.entry("int", int.class),
            Map.entry("long", long.class),
            Map.entry("float", float.class),
            Map.entry("double", double.class),
            Map.entry("void", void.class)
    );

    //Assumption is that if there's a node, then there's only 1 of that class.
    Map<Class<?>,Object> nodes = new HashMap<>();
    //Same thing here.
    Map<Class<?>, Object> resources = new HashMap<>();

    private Map<Class<?>, Constructor<?>> constructorCache = new HashMap<>();

    private TPFValueRepository valueRepository;
    private TPFMetadataFile metadataFile;

    public TPFNodeRepository(TPFValueRepository valueRepository, TPFMetadataFile metadataFile){
        this.valueRepository = valueRepository;
        this.metadataFile = metadataFile;
    }

    public <T> T getNode(Class<T> classType){
        return (T) this.nodes.getOrDefault(classType,null);
    }


    public void generateNodesAndResourcesV2(){
        //LinkedHashMap<String, ClassValueMetadata> createOrder = (LinkedHashMap<String, ClassValueMetadata>) metadataFile.classes;
        LinkedHashMap<String,ConstructorInformation> createOrder = metadataFile.constructorInformation;


        metadataFile.printMetadataFile();
        for(String qualifiedClassName : createOrder.keySet()){
            ConstructorInformation constructorInfo = metadataFile.constructorInformation.get(qualifiedClassName);
            ClassValueMetadata classData = metadataFile.classes.getOrDefault(qualifiedClassName, null);

            Class<?> wantedClass = null;
            try {
                wantedClass = Class.forName(qualifiedClassName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            System.out.println("Current Class: " + qualifiedClassName);

            Class<?>[] neededConstructorParams = getParameters(constructorInfo.neededConstructorParameters);
            Class<?>[] desiredConstructorParams = getParameters(constructorInfo.desiredConstructorParameters);

            Constructor<?> matchingConstructor = findConstructor(wantedClass, neededConstructorParams);

            String constructorSig = Arrays.stream(neededConstructorParams)
                    .map(paramClass -> "(" + paramClass.getCanonicalName() + ")")
                    .collect(Collectors.joining(",", "[", "]"));

            List<ParameterValueInfo> tpfValueParameters = (classData != null) ? classData.parameters.getOrDefault(constructorSig, null) : null;
            ParameterValueInfo[] mappedValues = new ParameterValueInfo[neededConstructorParams.length];
            if(tpfValueParameters != null){
                for(ParameterValueInfo inf : tpfValueParameters){
                    mappedValues[inf.positionInConstructor] = inf;
                }
            }

            Object[] params = new Object[neededConstructorParams.length];

            for(int i = 0; i<params.length;i++){
                Class<?> classOfCurrentParameter = desiredConstructorParams[i];
                //Okay, parameters don't retain their name, so I can't do it that way.

                if(mappedValues[i] != null){
                    ParameterValueInfo inf = mappedValues[i];
                    params[i] = valueRepository.getValue(inf.location, classOfCurrentParameter);
                    if(params[i] == null){
                        params[i] = TPFValueRepository.convertStringToType(inf.defaultValue,classOfCurrentParameter);
                    }

                    if(params[i] == null){
                        throw new RuntimeException("Unable to find the value at location " + inf.location +" and Unable to convert the default value " + inf.defaultValue + " to the type " + classOfCurrentParameter.getCanonicalName());
                    }
                }
                else
                {
                    params[i] = this.getNode(classOfCurrentParameter);
                }
            }

            try {
                System.out.println("Wanted class: " + wantedClass.getCanonicalName());
                System.out.println(Arrays.toString(params));
                System.out.println("Check Args: " + constructorSig);
                Object newObj = matchingConstructor.newInstance(params);
                nodes.put(wantedClass, newObj);
                valueRepository.injectFields(newObj);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            constructorCache.put(wantedClass, matchingConstructor);
        }
    }

    private Class<?>[] getParameters(List<String> constructorParamsNeeded){
        Class<?>[] parametersInCorrectConstructor = new Class<?>[constructorParamsNeeded.size()];

        for(int i = 0; i<constructorParamsNeeded.size();i++){
            String p = constructorParamsNeeded.get(i);

            if(PRIMITIVE_TYPE_MAP.containsKey(p)){
                parametersInCorrectConstructor[i] = PRIMITIVE_TYPE_MAP.get(p);
            }
            else
            {
                try {
                    parametersInCorrectConstructor[i] = Class.forName(p);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return parametersInCorrectConstructor;
    }
    private Constructor<?> findConstructor(Class<?> wantedClass, Class<?>[] parametersInCorrectConstructor){

        System.out.println("Need: " + Arrays.toString(parametersInCorrectConstructor));
        for(Constructor<?> ctor : wantedClass.getConstructors()){
            if(Arrays.equals(parametersInCorrectConstructor, ctor.getParameterTypes()))
            {
                System.out.println("\tFound match");
                return ctor;
            }
        }
        System.out.println("Failed to find match");

        return null;
    }


    /*
    //This should also be the class that reads the META-INF and generates the tree.
    public void generateNodesAndResources(){

        this.metadataFile.printMetadatFile();
        try(InputStream is = TPF.class.getClassLoader()
                .getResourceAsStream("META-INF/tpf/createorder.txt")) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                List<String> lines = reader.lines().toList();

                for(String line : lines){
                    int start = line.indexOf('(');
                    int end = line.indexOf(')');
                    String classToBeCreated = line.substring(0, start);
                    String parameterParts = line.substring(start+1,end);
                    String[] parameters;
                    if (parameterParts.isBlank()) {
                        parameters = new String[0];
                    } else {
                        parameters = Arrays.stream(parameterParts.split(","))
                                .map(String::trim)
                                .toArray(String[]::new);
                    }

                    Class<?> clazz = Class.forName(classToBeCreated);
                    Class<?>[] classParameters = new Class<?>[parameters.length];
                    for(int i = 0; i<parameters.length;i++){
                        classParameters[i] = Class.forName(parameters[i]);
                    }

                    Constructor<?> correctConstructor = findMatchingConstructor(clazz,classParameters);
                    if(correctConstructor == null){
                        continue;
                    }

                    ClassMetadata classMetadata = metadataFile.classes.get(classToBeCreated);
                    Object[] needed_parameters = new Object[correctConstructor.getParameterCount()];
                    Parameter[] constructorParameters = correctConstructor.getParameters();


                    System.out.println("Current Class: " + classToBeCreated);
                    if(classMetadata != null) {
                        for (int i = 0; i < needed_parameters.length; i++) {
                            Parameter current = constructorParameters[i];

                            FieldValueInfo inf = classMetadata.parameters.get(current.getName());

                            if (inf == null) {
                                // Fallback: try to find by type
                                Class<?> paramType = classParameters[i];

                                List<FieldValueInfo> matching = classMetadata.parameters.values().stream()
                                        .filter(m -> {
                                            try {
                                                return Class.forName(m.type).equals(paramType);
                                            } catch (ClassNotFoundException e) {
                                                return false;
                                            }
                                        })
                                        .toList();

                                if (matching.size() == 1) {
                                    inf = matching.get(0);
                                } else if (matching.size() > 1) {
                                    throw new IllegalStateException("Multiple TPFValue parameters match type " + paramType.getName()
                                            + " for constructor parameter " + current.getName());
                                }
                            }

                            // Either matched by name or by type
                            if (inf != null) {
                                Class<?> needed_type = Class.forName(inf.type);
                                Object value = valueRepository.getValue(inf.location, needed_type);
                                if (value == null) {
                                    if (inf.defaultValue != null && !inf.defaultValue.isBlank()) {
                                        value = TPFValueRepository.convertStringToType(inf.defaultValue, needed_type);
                                    } else {
                                        throw new IllegalStateException(classToBeCreated + " is missing required value for parameter: " + current.getName());
                                    }
                                }
                                needed_parameters[i] = value;
                            } else {
                                Object dep = nodes.get(classParameters[i]);
                                if (dep == null) {
                                    throw new IllegalStateException(classToBeCreated + " is missing dependency for constructor parameter: " + current.getName()
                                            + " of type " + classParameters[i].getName());
                                }
                                needed_parameters[i] = dep;
                            }
                        }
                    }
                    else
                    {
                        for(int i = 0; i<needed_parameters.length; i++)
                        {
                            Object dep = nodes.get(classParameters[i]);
                            if (dep == null) {
                                throw new IllegalStateException("Missing dependency for constructor parameter: " + classParameters[i].getName()
                                        + " of type " + classParameters[i].getName());
                            }
                            needed_parameters[i] = dep;
                        }
                    }

                    Object created_obj = correctConstructor.newInstance(needed_parameters);
                    nodes.put(clazz,created_obj);

                    if(classMetadata != null && created_obj != null){
                        for(String variableName : classMetadata.fields.keySet()){
                            FieldValueInfo inf = classMetadata.fields.get(variableName);
                            Field field = clazz.getDeclaredField(variableName);
                            Object value = valueRepository.getValue(inf.location, field.getType());

                            field.setAccessible(true);
                            System.out.println("Set field value: " + variableName);
                            field.set(created_obj, value);
                        }
                    }

                    System.out.println("Creating: " + classToBeCreated +
                            " with args: " + Arrays.toString(needed_parameters));
                }
            }
        } catch (IOException | InvocationTargetException | InstantiationException | IllegalAccessException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Finished creating dependencies");
    }
     */

    private Constructor<?> findMatchingConstructor(Class<?> clazz, Class<?>... argTypes) {
        for (Constructor<?> ctor : clazz.getConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length != argTypes.length) continue;

            boolean matches = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!paramTypes[i].isAssignableFrom(argTypes[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) return ctor;
        }
        return null; // no matching constructor found
    }

}
