package org.treepluginframework.values;

import org.treepluginframework.component_architecture.EventDispatcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TPFEventFile {
    //Class Name, Cache of method sigs.
    public Map<String,HashMap<String,MethodSignature>> methodCache = new HashMap<>();

    public TPFEventFile(){

    }

    public TPFEventFile(Map<String,HashMap<String,MethodSignature>> methodCache){
        this.methodCache = methodCache;
    }
}
