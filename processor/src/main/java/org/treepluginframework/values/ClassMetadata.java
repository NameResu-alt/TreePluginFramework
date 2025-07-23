package org.treepluginframework.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Map;

public class ClassMetadata {
    public Map<String, MemberValueInfo> parameters = new HashMap<>();
    public Map<String, MemberValueInfo> fields = new HashMap<>();

    @JsonIgnore
    public boolean isEmpty(){
        return parameters.isEmpty() && fields.isEmpty();
    }
}
