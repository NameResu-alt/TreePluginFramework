package org.treepluginframework.values;

public class FieldValueInfo {
    public String type;
    public String location;
    public String defaultValue;

    public FieldValueInfo(){

    }

    public FieldValueInfo(String type, String location, String s) {
        this.type = type;
        this.location = location;
        this.defaultValue = s;
    }

    @Override
    public String toString(){
        return type + " " + location + " " + defaultValue;
    }
}
