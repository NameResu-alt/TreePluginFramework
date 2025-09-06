package org.treepluginframework.values;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MethodSignature {
    public String originClass;
    public String methodName;
    public List<String> parameterTypes = new ArrayList<>();
    public int priority;
    public boolean expectsAdapter;
    public boolean useSubClasses;
    public boolean isPrivate;
    /***
     * If true, means that this method is an interface(not default)/abstract method.
     */
    public boolean notImplementation;

    public MethodSignature(){

    }

    public MethodSignature(String originClass, String methodName, List<String> parameterNames, int priority, boolean expectsAdapter, boolean useSubClasses, boolean isPrivate, boolean notImplementation){
        this.originClass = originClass;
        this.methodName = methodName;
        this.parameterTypes = parameterNames;

        this.priority = priority;
        this.expectsAdapter = expectsAdapter;
        this.useSubClasses = useSubClasses;
        this.isPrivate = isPrivate;
        this.notImplementation = notImplementation;
    }

    //If the method name is the same,
    //And they have the same methods
    //It's the same method signature.

    @Override
    public boolean equals(Object other){
        if(!(other instanceof MethodSignature sig)) return false;

        if(!methodName.equals(sig.methodName)) return false;

        if(parameterTypes.size() != sig.parameterTypes.size()) return false;

        for(int i = 0; i<parameterTypes.size();i++){
            if(!parameterTypes.get(i).equals(sig.parameterTypes.get(i))){
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodName, parameterTypes);
    }

    @Override
    public String toString(){
        return originClass + " - " + methodName+"("+parameterTypes.toString()+")";
    }
}
