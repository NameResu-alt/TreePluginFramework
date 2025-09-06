package org.treepluginframework.values;

import org.treepluginframework.component_architecture.EventDispatcher;

import java.util.*;

public class TPFEventFile {
    //Class Name, Cache of method sigs.
    //public Map<String,HashMap<String,MethodSignature>> methodCache = new HashMap<>();
    //Class Name, EventType, MethodSignature.
    public Map<String,HashMap<String, HashSet<MethodSignature>>> methodCache = new HashMap<>();
    public Date timeCreated;

    public TPFEventFile(){

    }

    public TPFEventFile(Map<String,HashMap<String, HashSet<MethodSignature>>> methodCache){
        this.methodCache = methodCache;
        this.timeCreated = new Date();
    }
}
