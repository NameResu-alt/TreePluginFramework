package org.treepluginframework.component_architecture;

import org.treepluginframework.events.EventAdapter;
import org.treepluginframework.events.IEvent;
import org.treepluginframework.events.NativeEventAdapter;
import org.treepluginframework.values.ConstructorInformation;
import org.treepluginframework.values.MethodSignature;
import org.treepluginframework.values.TPFEventFile;
import org.treepluginframework.values.TPFMetadataFile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class TPFEventDispatcher {
    private DAG graph = new DAG();

    private HashMap<Class<?>, HashMap<Class<?>, HandlerHolder>> cachedMethods = new HashMap<>();

    private List<Object[]> queuedObjects = new ArrayList<>();

    private TPFEventFile eventFile;
    private TPFMetadataFile metaFile;

    private TPFNodeRepository nodeRepository;

    private boolean dispatchOnGoing = false;

    public TPFEventDispatcher(TPFMetadataFile metaFile, TPFEventFile eventFile, TPFNodeRepository nodeRepository){
        this.metaFile = metaFile;
        this.eventFile = eventFile;
        this.nodeRepository = nodeRepository;
        calculateCachedMethods();
    }

    public void register(Object parent, Object component, boolean afterCurrentEvent){
        if(!afterCurrentEvent)
        {
            graph.addEdge(parent, component);
        }
        else
        {
            queuedObjects.add(new Object[]{parent,component});
        }
    }

    public void unregister(Object obj){
        graph.removeNode(obj);
    }

    // For events that implement IEvent — wraps them in a NativeEventAdapter
    public void emit(Object fromComponent, IEvent event){
        if (event == null) throw new IllegalArgumentException("Event cannot be null");
        dispatchOnGoing = true;
        dispatch(fromComponent, new NativeEventAdapter(event));
        finishedDispatch();
    }

    // For external or generic events — assumes a custom adapter is already provided
    public void emit(Object fromComponent, EventAdapter<?> adapter){
        if (adapter == null) throw new IllegalArgumentException("Adapter cannot be null");
        dispatchOnGoing = true;
        dispatch(fromComponent, adapter);
        finishedDispatch();
    }

    private void finishedDispatch(){
        this.dispatchOnGoing = false;

        for(Object[] o : queuedObjects)
        {
            this.register(o[0],o[1],false);
        }

        this.queuedObjects.clear();
    }


    //Only going to deal with downstream events right now.
    private void dispatch(Object component, EventAdapter<?> adapter) {
        if (component == null) {
            System.out.println("Attempted to dispatch an event with a null component: " + adapter.getEvent().getClass());
            return;
        }

        if(adapter.isPropagationStopped()) return;

        Class<?> componentClass = component.getClass();
        Class<?> eventType = adapter.getEffectiveEventType();

        // Handle the component itself if it has a handler
        Map<Class<?>, HandlerHolder> componentHandlers = cachedMethods.get(componentClass);
        if (componentHandlers != null) {
            HandlerHolder handler = componentHandlers.get(eventType);
            if (handler != null) {
                try {
                    if (handler.expectsAdapter) {
                        handler.method.invoke(component, adapter.getEvent(), adapter);
                    } else {
                        handler.method.invoke(component, adapter.getEvent());
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Get children and split into priority and non-priority
        Set<Object> children = graph.getChildren(component);
        List<Object> priorityChildren = new ArrayList<>();
        List<Object> otherChildren = new ArrayList<>();

        for (Object child : children) {
            Map<Class<?>, HandlerHolder> childHandlers = cachedMethods.get(child.getClass());
            HandlerHolder childHandler = (childHandlers != null) ? childHandlers.get(eventType) : null;

            if (childHandler != null) {
                priorityChildren.add(child);
            } else {
                otherChildren.add(child);
            }
        }

        // Sort priority children by descending priority
        priorityChildren.sort(Comparator.comparingInt(
                o -> -cachedMethods.get(o.getClass()).get(eventType).priority
        ));

        // Dispatch recursively
        for (Object child : priorityChildren) {
            dispatch(child, adapter);
            if(adapter.isPropagationStopped()) return;
        }

        for (Object child : otherChildren) {
            dispatch(child, adapter);
            if(adapter.isPropagationStopped()) return;
        }
    }



    public void setUpDAG(){

        if(metaFile == null) return;

        LinkedHashMap<String, ConstructorInformation> constructorInfo = metaFile.constructorInformation;
        for(Map.Entry<String,ConstructorInformation> entry : constructorInfo.entrySet()){
            String className = entry.getKey();
            ConstructorInformation inf = entry.getValue();

            Class<?> nodeType = null;
            try {
                nodeType = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            System.out.println("Class: " + className + " Has Dependencies: " + inf.dependencies);

            Object correspondingNode = nodeRepository.getNode(nodeType);
            if(inf.dependencies.isEmpty()){
                graph.addNode(correspondingNode);
            }
            else
            {
                for(String qualifiedDependencyName : inf.dependencies){
                    Class<?> dependencyType = null;
                    try {
                        dependencyType = Class.forName(qualifiedDependencyName);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                    Object dependency = nodeRepository.getNode(dependencyType);

                    graph.addEdge(correspondingNode, dependency);
                }
            }
        }

        graph.printGraph();
    }

    private void calculateCachedMethods(){
        if(eventFile == null) return;

        Map<String, HashMap<String, MethodSignature>> preCache = eventFile.methodCache;

        for(String qualifiedClassName : preCache.keySet()){
            Class<?> currentClass = null;
            try {
                currentClass = Class.forName(qualifiedClassName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            HashMap<Class<?>, HandlerHolder> cache = cachedMethods.computeIfAbsent(currentClass, k -> new HashMap<>());

            HashMap<String,MethodSignature> methodsToCache = preCache.get(qualifiedClassName);
            for(String qualifiedEventClassName : methodsToCache.keySet()){
                MethodSignature sig = methodsToCache.get(qualifiedEventClassName);

                Class<?> eventType;
                try {
                    eventType = Class.forName(qualifiedEventClassName);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }

                Class<?>[] parameterTypes = new Class[sig.parameterTypes.size()];

                for(int i = 0; i<sig.parameterTypes.size();i++){
                    String parameterType = sig.parameterTypes.get(i);
                    Class<?> paramClass = null;
                    try {
                        paramClass = Class.forName(parameterType);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                    parameterTypes[i] = paramClass;
                }

                String methodName = sig.methodName;

                Method method = null;
                try {
                    method = currentClass.getDeclaredMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
                method.setAccessible(true);
                HandlerHolder hold = new HandlerHolder(method, sig.priority, sig.expectsAdapter);
                cache.put(eventType, hold);
            }
        }
    }


    // Internal holder for a handler method and its priority
    private class HandlerHolder {
        final Method method;
        final int priority;
        final boolean expectsAdapter;

        HandlerHolder(Method method, int priority, boolean expectsAdapter) {
            this.method = method;
            this.priority = priority;
            this.expectsAdapter = expectsAdapter;
        }
    }
}
