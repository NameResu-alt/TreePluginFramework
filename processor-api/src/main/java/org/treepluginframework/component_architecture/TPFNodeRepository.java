package org.treepluginframework.component_architecture;

import org.treepluginframework.values.*;

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

    private Map<Class<?>, Constructor<?>> constructorCache = new HashMap<>();

    private TPFValueRepository valueRepository;
    private TPFStructureFile metadataFile;
    private TPFValueFile valueFile;
    private TPF mainTPF;

    public TPFNodeRepository(TPF mainTPF, TPFValueRepository valueRepository, TPFStructureFile metadataFile, TPFValueFile valueFile){
        this.mainTPF = mainTPF;
        this.valueRepository = valueRepository;
        this.metadataFile = metadataFile;
        this.valueFile = valueFile;
    }

    public <T> T getNode(Class<T> classType){
        return (T) this.nodes.getOrDefault(classType,null);
    }


    /*
        This is broke for now.
     */
    public void generateNodesAndResourcesV2(){
        //LinkedHashMap<String, ClassValueMetadata> createOrder = (LinkedHashMap<String, ClassValueMetadata>) metadataFile.classes;
        LinkedHashMap<String,ConstructorInformation> createOrder = metadataFile.constructorInformation;


        metadataFile.printMetadataFile();
        for(String qualifiedClassName : createOrder.keySet()){
            ConstructorInformation constructorInfo = metadataFile.constructorInformation.get(qualifiedClassName);
            ClassValueMetadataV2 classData2 = valueFile.classData.getOrDefault(qualifiedClassName, null);


            Class<?> wantedClass = null;
            try {
                wantedClass = Class.forName(qualifiedClassName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            System.out.println("\n\n////Current Class: " + qualifiedClassName);

            Class<?>[] neededConstructorParams = getParameters(constructorInfo.neededConstructorParameters);
            Class<?>[] desiredConstructorParams = getParameters(constructorInfo.desiredConstructorParameters);

            Constructor<?> matchingConstructor = findConstructor(wantedClass, neededConstructorParams);

            /*String constructorSig = Arrays.stream(neededConstructorParams)
                    .map(paramClass -> "(" + paramClass.getCanonicalName() + ")")
                    .collect(Collectors.joining(",", "[", "]"));
            */
            String constructorSig = Arrays.stream(neededConstructorParams)
                    .map(Class::getCanonicalName)
                            .collect(Collectors.joining(",","[","]"));


            HashMap<Integer,VariableValueInfoV2> parameterValueInfo = (classData2 != null) ? classData2.constructors.getOrDefault(constructorSig,new HashMap<>()) : new HashMap<>();

            System.out.println("Info from class Data: " + parameterValueInfo);
            //classData2.constructors.getOrDefault(construtor)
            Object[] params = new Object[neededConstructorParams.length];

            HashMap<String, TPFValueRepository.FileValueRequest> fileRequests = new HashMap<>();
            for(int i = 0; i<params.length;i++){
                Class<?> classOfCurrentParameter = desiredConstructorParams[i];
                //Okay, parameters don't retain their name, so I can't do it that way.

                //Can take in the actual TPF class itself as a constructor parameter, if needed.
                if(classOfCurrentParameter == TPF.class){
                    params[i] = mainTPF;
                    continue;
                }

                if(parameterValueInfo.containsKey(i)){
                    VariableValueInfoV2 inf = parameterValueInfo.get(i);
                    if(inf.fileName.isBlank()){
                        //Global one, simple enough.
                        params[i] = valueRepository.getGlobalValue(inf.location, classOfCurrentParameter);
                    }
                    else
                    {
                        TPFValueRepository.FileValueRequest request = fileRequests.computeIfAbsent(inf.fileName,k-> new TPFValueRepository.FileValueRequest(k));
                        final int copy = i;
                        System.out.println(inf.fileName + " " + inf.location + " " + classOfCurrentParameter);
                        request.addWantedValue(inf.location, classOfCurrentParameter, new TPFValueRepository.FileValueCallback() {
                            @Override
                            public void valueReceived(Object value) {
                                params[copy] = value;
                                System.out.println("Got the value: " + value);
                            }

                            @Override
                            public void failedToFindValue() {
                                params[copy] = TPFValueRepository.convertStringToType(inf.defaultValue,classOfCurrentParameter);
                                System.out.println("Didn't get the value: " + params[copy]);
                            }
                        });

                    }
                }
                else
                {
                    params[i] = this.getNode(classOfCurrentParameter);
                }
            }

            for(TPFValueRepository.FileValueRequest req : fileRequests.values()){
                valueRepository.getFileValues(req);
            }

            System.out.println("\tParams: " + Arrays.toString(params));

            try {
                System.out.println("Wanted class: " + wantedClass.getCanonicalName());
                //System.out.println(Arrays.toString(params));
                System.out.println("Check Args: " + constructorSig);
                Object newObj = matchingConstructor.newInstance(params);
                nodes.put(wantedClass, newObj);
                valueRepository.injectFields(newObj);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            constructorCache.put(wantedClass, matchingConstructor);
            System.out.println("\tDone with " + wantedClass);
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
