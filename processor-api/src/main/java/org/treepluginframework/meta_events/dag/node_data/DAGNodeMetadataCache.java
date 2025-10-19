package org.treepluginframework.meta_events.dag.node_data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DAGNodeMetadataCache {
    private static final Map<Class<?>, List<Field>> classFields = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Method>> classMethods = new ConcurrentHashMap<>();


    public static DAGNodeMetadata getMetadata(UUID nodeUUID, Object o){
        if(o == null){
            throw new NullPointerException("Attempted to get the DAGNodeMetadata of a null object. Node UUID: " + nodeUUID);
        }
        Class<?> objectClass = o.getClass();
        getAllData(objectClass);

        List<Field> fields = classFields.get(objectClass);
        List<Method> methods = classMethods.get(objectClass);


        return new DAGNodeMetadata(nodeUUID, objectClass, fields, methods);
    }

    private static void getAllData(Class<?> clazz) {
        if(classFields.containsKey(clazz)) return;

        while (clazz != null && clazz != Object.class) {
            classFields.computeIfAbsent(clazz,k->Arrays.asList(k.getDeclaredFields()));
            classMethods.computeIfAbsent(clazz, k->Arrays.asList(k.getDeclaredMethods()));

            clazz = clazz.getSuperclass();
        }
    }
}
