package org.treepluginframework.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

public class VariableValueInfo {
    @JsonIgnore
    public String originClass;
    @JsonIgnore
    public String fieldName;
    public String type;
    public String fileName = "";
    public String location;
    public String defaultValue;
    //Private variables will still be part of the child class, they can't be shadowed.
    public boolean isPrivate;

    public VariableValueInfo(){

    }

    public VariableValueInfo(String originClass, String fieldName, String type, String fileName, String location, String s, boolean isPrivate) {
        this.originClass = originClass;
        this.fieldName = fieldName;
        this.type = type;
        this.fileName = fileName;
        this.location = location;
        this.defaultValue = s;
        this.isPrivate = isPrivate;
    }

    @Override
    public boolean equals(Object other){
        if(!(other instanceof VariableValueInfo compare)) return false;

        if(!fieldName.equals(compare.fieldName)) return false;

        //I think this is redundant, but just in case
        if(!type.equals(compare.type)) return false;

        //If either of these two are private, they are not the same.
        return !(isPrivate || compare.isPrivate);
    }

    @Override
    public int hashCode(){
        return Objects.hash(fieldName,type, isPrivate);
    }

}
