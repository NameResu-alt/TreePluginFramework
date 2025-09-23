package org.treepluginframework.values;

import java.util.HashMap;
import java.util.Map;

public class ClassValueMetadata {
    //I need to keep information of all constructors of a class.
    //I need to keep information of all fields of a class.
    //Origin Class, field name, fieldValueInfo
    //A single class can't have the same variable name more than once, so this is fine.
    /***
     * Origin class, Field name, VariableValueInfo
     */
    public HashMap<String,HashMap<String, VariableValueInfo>> fields = new HashMap<>();
    //Constructor signature, position in constructor, FieldValueInfo
    /***
     * Constructor signature, Position in constructor, VariableValueInfo
     */
    public Map<String, HashMap<Integer, VariableValueInfo>> constructors = new HashMap<>();

    public ClassValueMetadata(){

    }

    public void merge(ClassValueMetadata otherMetadata){
        fields.putAll(otherMetadata.fields);
        constructors.putAll(otherMetadata.constructors);
    }
}
