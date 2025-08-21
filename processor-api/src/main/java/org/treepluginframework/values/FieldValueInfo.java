package org.treepluginframework.values;

public class FieldValueInfo {
    public String type;
    public String fileName = "";
    public String location;
    public String defaultValue;

    public FieldValueInfo(){

    }

    public FieldValueInfo(String type, String fileName, String location, String s) {
        this.type = type;
        this.fileName = fileName;
        this.location = location;
        this.defaultValue = s;
    }

    @Override
    public String toString(){
        if(fileName.isEmpty())
        {
            return type + " " + location + " " + defaultValue;
        }
        else
        {
            return type + " " + fileName + " " + location + " " + defaultValue;
        }

    }
}
