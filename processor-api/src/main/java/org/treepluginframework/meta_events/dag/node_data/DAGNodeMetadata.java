package org.treepluginframework.meta_events.dag.node_data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;


public class DAGNodeMetadata {
    private final UUID uuid;
    private final Class<?> objectClass;
    private final List<Field> classFields;
    private final List<Method> classMethods;;

    DAGNodeMetadata(UUID uuid, Class<?> objectClass, List<Field> fields, List<Method> methods){
        this.uuid = uuid;
        this.objectClass = objectClass;
        this.classFields = fields;
        this.classMethods = methods;
    }
}
