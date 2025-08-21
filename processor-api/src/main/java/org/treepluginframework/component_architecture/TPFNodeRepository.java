package org.treepluginframework.component_architecture;

import org.treepluginframework.values.ClassValueMetadata;
import org.treepluginframework.values.ConstructorInformation;
import org.treepluginframework.values.ParameterValueInfo;
import org.treepluginframework.values.TPFMetadataFile;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

    private TPF mainTPF;

    public TPFNodeRepository(TPF mainTPF, TPFValueRepository valueRepository, TPFMetadataFile metadataFile){
        this.mainTPF = mainTPF;
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

                //Can take in the actual TPF class itself as a constructor parameter, if needed.
                if(classOfCurrentParameter == TPF.class){
                    params[i] = mainTPF;
                    continue;
                }

                if(mappedValues[i] != null){
                    ParameterValueInfo inf = mappedValues[i];
                    params[i] = valueRepository.getGlobalValue(inf.location, classOfCurrentParameter);
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


            System.out.println("\tParams: " + Arrays.toString(params));

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
