package org.treepluginframework.WiringSystem;

import org.treepluginframework.EventSystem.ComponentArchitecture.EventDispatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TPFContext {
    private static final Map<Class<?>, Object> registry = new HashMap<>();
    private static final Map<Class<?>,TPFAutoWireCache> cachedFields = new HashMap<>();

    private static class TPFAutoWireCache{
        private HashMap<Class<?>,Field> wireFields = new HashMap<>();
        private TPFAutoWireCache()
        {
        }

        public void addField(Class<?> type, Field field)
        {
            this.wireFields.put(type,field);
        }

        public Field getCachedField(Class<?> type){
            return wireFields.get(type);
        }

        public Set<Map.Entry<Class<?>, Field>> getEntries(){
            return wireFields.entrySet();
        }

    }


    private static void traverseAndRegister(Map<Class<?>, Set<Class<?>>> graph){
        Set<Class<?>> allChildren = new HashSet<>();
        for(Set<Class<?>> children : graph.values()){
            allChildren.addAll(children);
        }

        // Roots are keys not present as any child
        Set<Class<?>> roots = new HashSet<>(graph.keySet());
        roots.removeAll(allChildren);

        Set<Class<?>> visited = new HashSet<>();

        for (Class<?> root : roots) {
            dfsRegister(root, null, graph, visited);
        }
    }

    private static void dfsRegister(Class<?> current, Class<?> parent,
                     Map<Class<?>, Set<Class<?>>> graph, Set<Class<?>> visited) {
        if(visited.contains(current))
        {
            System.out.println(visited);
            throw new IllegalStateException("A node has been visited more than once!: Child: " + current.getSimpleName() + " Parent: " +  (parent == null ? "null" : parent.getSimpleName()));
        }
        visited.add(current);
        Object parentObj = registry.getOrDefault(parent,null);
        Object childObj = registry.get(current);

        EventDispatcher.getInstance().registerComponent(childObj,parentObj);

        for (Class<?> child : graph.getOrDefault(current, Set.of())) {
            dfsRegister(child, current, graph, visited);
        }
    }

    //Reflection Cache necessary here.

    static {

        System.out.println("I'm running the static block for the TPFContext, should be newest version!");
        Set<Class<?>> containsAutoWire = new HashSet<>();
        HashMap<Class<?>,Set<Class<?>>> children = new HashMap<>();

        System.out.println("ClassLoaderName: " + TPFContext.class.getClassLoader().getName());

        try(InputStream is = TPFContext.class.getClassLoader().getSystemResourceAsStream("META-INF/tpf-context/auto-child-wires")) {
            if(is != null){
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                List<String> lines = reader.lines().collect(Collectors.toList());
                System.out.println("Lines in auto-child-wires: " + lines.size());
                lines.forEach(line -> System.out.println("  Line: " + line));

                new BufferedReader(new InputStreamReader(is)).lines().forEach(classPair ->{
                    try {
                        String[] info = classPair.split(",");
                        String originClassName = info[0];
                        String fieldName = info[1];
                        String targetClassName = info[2];

                        Class<?> originClass = Class.forName(originClassName);
                        Class<?> targetClass = Class.forName(targetClassName);
                        containsAutoWire.add(originClass);

                        children.computeIfAbsent(originClass,k->new HashSet<>()).add(targetClass);

                        TPFAutoWireCache cache = cachedFields.computeIfAbsent(originClass, k -> new TPFAutoWireCache());

                        System.out.println("Class " + originClassName + " got DI of " + targetClassName);
                        Field field = originClass.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        cache.addField(targetClass,field);
                    } catch (Exception e) {
                        System.err.println("Failed to wire pair: " + classPair);
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Could not read auto-wires file.");
            e.printStackTrace();
        }

        HashMap<Class<?>,Set<Class<?>>> graph = new HashMap<>();
        List<Object> objectsToInject = new ArrayList<>();
        try (InputStream is = TPFContext.class.getClassLoader().getSystemResourceAsStream("META-INF/tpf-context/auto-node")) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                List<String> lines = reader.lines().collect(Collectors.toList());
                System.out.println("Lines in auto-node: " + lines.size());
                lines.forEach(line -> System.out.println("  Line: " + line));
                new BufferedReader(new InputStreamReader(is)).lines().forEach(className -> {
                    try {
                        Class<?> clazz = Class.forName(className);
                        graph.put(clazz, children.getOrDefault(clazz, new HashSet<>()));

                        Object instance = clazz.getDeclaredConstructor().newInstance();
                        if(containsAutoWire.contains(clazz))
                        {
                            objectsToInject.add(instance);
                        }

                        System.out.println("TPFContext added class of type " + className);
                        registry.put(clazz, instance);
                    } catch (Exception e) {
                        System.err.println("Failed to load class into TPFContext: " + className);
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Could not read auto-node file.");
            e.printStackTrace();
        }


        for(Object obj : objectsToInject)
        {
            inject(obj);
        }

        //Now here's the event Dispatcher....
        traverseAndRegister(graph);
    }

    //TODO: I need to cache whatever needs the injection beforehand.
    //Need to do something similar for the event system as well tbh.
    /**
     * Injects dependencies into the given existing object instance and returns it.
     */

    public static <T> T inject(T obj) {
        Class<?> clazz = obj.getClass();

        if(cachedFields.containsKey(clazz))
        {
            TPFAutoWireCache cache = cachedFields.get(clazz);
            for(Map.Entry<Class<?>, Field> entry : cache.getEntries())
            {
                Class<?> dependencyType = entry.getKey();
                Object dependency = registry.get(dependencyType);
                if(dependency != null)
                {
                    try {
                        entry.getValue().set(obj,dependency);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                else
                {
                    throw new RuntimeException("No bean found for type " + dependencyType.getSimpleName()+ " to inject into " + clazz.getName());
                }
            }
        }
        else
        {
            Logger.getLogger(TPFContext.class.getName()).log(Level.WARNING,
                    "Attempted to inject into a class that has no autowires. {0}",
                    new Object[]{clazz.getName()});

        }

        return obj;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        return (T) registry.getOrDefault(type,null);
    }

    public static boolean contains(Class<?> type) {
        return registry.containsKey(type);
    }

    public static void init(){

    }




}
