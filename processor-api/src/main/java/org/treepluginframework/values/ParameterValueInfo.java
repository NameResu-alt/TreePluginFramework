package org.treepluginframework.values;

public class ParameterValueInfo {
    public String type;
    public String location;
    public String defaultValue;
    public int positionInConstructor;

    public ParameterValueInfo(){

    }

    public ParameterValueInfo(String type, String location, String s, int positionInConstructor) {
        this.type = type;
        this.location = location;
        this.defaultValue = s;
        this.positionInConstructor = positionInConstructor;
    }
}
