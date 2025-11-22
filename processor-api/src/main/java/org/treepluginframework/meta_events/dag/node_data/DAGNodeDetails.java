package org.treepluginframework.meta_events.dag.node_data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;


public class DAGNodeDetails {
    private final UUID nodeUUID;
    private final Class<?> objectClass;
    private final List<Field> classFields;
    private final List<Method> classMethods;;

    DAGNodeDetails(UUID nodeUUID, Class<?> objectClass, List<Field> fields, List<Method> methods){
        this.nodeUUID = nodeUUID;
        this.objectClass = objectClass;
        this.classFields = fields;
        this.classMethods = methods;
    }

    public UUID getNodeUUID() {
        return nodeUUID;
    }

    public Class<?> getObjectClass() {
        return objectClass;
    }

    public List<Field> getClassFields() {
        return classFields;
    }

    public List<Method> getClassMethods() {
        return classMethods;
    }
}
