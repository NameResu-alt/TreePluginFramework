package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.values.ClassMetadata;
import org.treepluginframework.values.MemberValueInfo;
import org.treepluginframework.values.TPFMetadataFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TPFNodeRepository {
    //I need to store the nodes that I got in here.
    //Just putting them into the dispatcher doesn't seem like a sound decision.
    //I'm assuming that there will only ever be 1 type of node, maybe better way?
    Map<Class<?>,Object> nodes = new HashMap<>();
    //Same thing here.
    Map<Class<?>, Object> resources = new HashMap<>();

    Map<String,Object> aliases = new HashMap<>();

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

                            MemberValueInfo inf = classMetadata.parameters.get(current.getName());

                            if (inf == null) {
                                // Fallback: try to find by type
                                Class<?> paramType = classParameters[i];

                                List<MemberValueInfo> matching = classMetadata.parameters.values().stream()
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
                            MemberValueInfo inf = classMetadata.fields.get(variableName);
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
