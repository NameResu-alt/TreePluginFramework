package org.treepluginframework.meta_events.dag.node_data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DAGNodeMetadataCache {
    private static final Map<Class<?>, List<Field>> classFields = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Method>> classMethods = new ConcurrentHashMap<>();


    public static DAGNodeDetails getNodeDetails(UUID nodeUUID, Object o){
        //Probably the first step.
        if(nodeUUID == null && o == null) return null;

        if(o == null){
            throw new NullPointerException("Attempted to get the DAGNodeMetadata of a null object. Node UUID: " + nodeUUID);
        }
        Class<?> objectClass = o.getClass();
        getAllData(objectClass);

        List<Field> fields = classFields.get(objectClass);
        List<Method> methods = classMethods.get(objectClass);
        return new DAGNodeDetails(nodeUUID, objectClass, fields, methods);
    }

    public static DAGNodeReference getNodeReference(UUID nodeUUID, Object o){
        if(nodeUUID == null && o == null) return null;

        if(o == null){
            throw new NullPointerException("Attempted to get the DAGNodeReference of a null object. Node UUID: " + nodeUUID);
        }

        return new DAGNodeReference(nodeUUID, o.getClass(), "11/3/2025 - Nothing yet");
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
