package org.treepluginframework.values;

public class MemberValueInfo {
    public String type;
    public String location;
    public String defaultValue;

    public MemberValueInfo(){

    }

    public MemberValueInfo(String type, String location, String s) {
        this.type = type;
        this.location = location;
        this.defaultValue = s;
    }
}
