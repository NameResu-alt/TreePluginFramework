package org.treepluginframework.values;

import java.util.ArrayList;
import java.util.List;

public class MethodSignature {
    public String methodName;
    public List<String> parameterTypes = new ArrayList<>();
    public int priority;
    public boolean expectsAdapter;

    public MethodSignature(){

    }

    public MethodSignature(String methodName, List<String> parameterNames, int priority, boolean expectsAdapter){
        this.methodName = methodName;
        this.parameterTypes = parameterNames;

        this.priority = priority;
        this.expectsAdapter = expectsAdapter;
    }
}
