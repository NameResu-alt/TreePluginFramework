package org.treepluginframework.component_architecture;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.events.EventAdapter;
import org.treepluginframework.events.IEvent;
import org.treepluginframework.events.NativeEventAdapter;
import org.treepluginframework.values.ConstructorInformation;
import org.treepluginframework.values.MethodSignature;
import org.treepluginframework.values.TPFEventFile;
import org.treepluginframework.values.TPFStructureFile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class TPFEventDispatcher {
    private DAG<Object> graph = new DAG<Object>();

    //Class type, event type, HandlerHolder.
    //One event type can have multiple HandlerHolders, if whatever the eventtype was an interface.
    private HashMap<Class<?>, HashMap<Class<?>, ArrayList<HandlerHolder>>> cachedMethods = new HashMap<>();
    //The class of the object that has the methods.
    //The list of classes that have already been resolved.
    private HashMap<Class<?>,Set<Class<?>>> firstTimeClassUsedEventType = new HashMap<>();

    private List<Object[]> queuedObjects = new ArrayList<>();

    private TPFEventFile eventFile;
    private TPFStructureFile metaFile;

    private TPFNodeRepository nodeRepository;

    private boolean dispatchOnGoing = false;

    public TPFEventDispatcher(TPFStructureFile metaFile, TPFEventFile eventFile, TPFNodeRepository nodeRepository){
        this.metaFile = metaFile;
        this.eventFile = eventFile;
        this.nodeRepository = nodeRepository;
        calculateCachedMethods();
    }

    public void register(Object parent, Object component, boolean afterCurrentEvent){
        //if the graph doesn't contain the parent, nor the component.
        //I need to see if parent or component is an anonymous inner class.
        //If that's the case, I need to do a runtime cache of its HandlerHolders.
        if(!afterCurrentEvent)
        {
            graph.addEdge(parent, component);
        }
        else
        {
            queuedObjects.add(new Object[]{parent,component});
        }

        //If parent, or component is an anonymous inner class...
        //I need to do a runtime register.
        //Go through all its methods, and check to see what is good.
        parent.getClass().isAnonymousClass();
    }

    //It'd mainly be for anonymous inner classes. Wondering if its worth the headache
    //I need to make sure that whatever methods I'm registering here are from the anonymous inner class itself, and not the base class.
    //Actually, getDeclaredMethods covers this, so nevermind.
    //Which now makes me wonder, what does EventSubscription do if I extend the class?
    private void runtimeRegister(Object obj, EventAdapter<?> adapter){
        Method[] methods = obj.getClass().getDeclaredMethods();
        for(Method m : methods){
            if(!m.isAnnotationPresent(EventSubscription.class)) continue;

            Class<?>[] variables = m.getParameterTypes();
            if(variables.length == 0){
                continue;
            }

            if(variables.length > 2){
                continue;
            }

            if(variables[0].isPrimitive()){
                //Don't allow for primitives.
                continue;
            }

            if(variables[0].isAssignableFrom(List.class) || variables[0].isAssignableFrom(Map.class) || variables[0].isArray())
            {
                //Don't allow for lists, maps, or arrays, again.
                continue;
            }

            if(variables.length == 2){
                Class<?> adapterType = variables[1];
                if(!adapterType.isAssignableFrom(EventAdapter.class)) continue;
                //Type erasure, oh no.
                //At this point, I'm forced to assume that the user knows what they are doing.
                //The pro is that the dispatch system prevents incompatible events and adapters from being called.
                //So, that's fine.
            }
            //Then I'll need a map to store the handler holders and stuff.
            //It'll be in addition to whatever handlers the anonymous inner class has.
        }
    }

    public void registerLogger(Object logger){

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
        //Map<Class<?>, HandlerHolder> componentHandlers = cachedMethods.get(componentClass);
        Map<Class<?>, ArrayList<HandlerHolder>> componentHandlers = cachedMethods.get(componentClass);

        /// TODO: If the current class doesn't have a handler for it, I need to search its superclasses to see if they do.
        /// If that's the case, just take that method and put it in the component handlers.
        /// I'll also need to have something to make sure that I don't repeat this search with class type and event type.
        if(componentHandlers != null){
            ArrayList<HandlerHolder> handlers = componentHandlers.getOrDefault(eventType,null);
            boolean runtimeCheck = handlers == null || !firstTimeClassUsedEventType.containsKey(componentClass) || !firstTimeClassUsedEventType.get(componentClass).contains(eventType);

            if(runtimeCheck){
                System.out.println("Runtime Check occurred for: " + componentClass.getName());
                if(handlers == null){
                    //I didn't find an event specifically for this class type. I need to look at superclasses.
                    //Walk up the type hierarchy and see if I have a holder that does allow for this.
                    //Then, I need to see if that handler allows the use of subClasses.
                    //If it doesn't, skip it. If it does, stop.
                    Class<?> current = eventType.getSuperclass();
                    while(current != null){
                        if(componentHandlers.containsKey(current)){
                            ArrayList<HandlerHolder> potentialCandidates = componentHandlers.get(current);
                            ArrayList<HandlerHolder> viableCandidates = new ArrayList<>();

                            for(HandlerHolder holder : potentialCandidates){
                                if(holder.useSubClasses){
                                    viableCandidates.add(holder);
                                }
                            }

                            if(!viableCandidates.isEmpty()){
                                handlers = viableCandidates;
                                componentHandlers.put(eventType,viableCandidates);
                                break;
                            }
                        }
                        current = current.getSuperclass();
                    }
                    //Sort based on priority.
                    if(handlers != null){
                        Collections.sort(handlers, new Comparator<HandlerHolder>() {
                            @Override
                            public int compare(HandlerHolder o1, HandlerHolder o2) {
                                return o2.priority - o1.priority;
                            }
                        });
                    }
                }

                //Now I need to walk up the superclass again, but this time, I need to do it with interface checks.

                ArrayList<HandlerHolder> interfaceHandlers = new ArrayList<>();
                HashSet<Class<?>> foundInterfaces = new HashSet<>();

                Class<?> current = eventType;
                while(current != null){
                    checkInterfaces(current,componentHandlers,interfaceHandlers, foundInterfaces);
                    current = current.getSuperclass();
                }

                Collections.sort(interfaceHandlers, new Comparator<HandlerHolder>() {
                    @Override
                    public int compare(HandlerHolder o1, HandlerHolder o2) {
                        return o2.priority - o1.priority;
                    }
                });

                if(handlers == null){
                    handlers = interfaceHandlers;
                    componentHandlers.put(eventType,interfaceHandlers);
                }
                else
                {
                    handlers.addAll(interfaceHandlers);
                }

                firstTimeClassUsedEventType.computeIfAbsent(componentClass, k->new HashSet<>()).add(eventType);
            }


            if(handlers != null){
                for(HandlerHolder handler : handlers){
                    try {
                        if (handler.expectsAdapter) {
                            /// TODO: Log that the event was skipped due to adapter mismatch
                            if(adapter.getClass().isAssignableFrom(handler.adapterType))
                                handler.method.invoke(component, adapter.getEvent(), adapter);
                        } else {
                            handler.method.invoke(component, adapter.getEvent());
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        Set<Object> children = graph.getChildren(component);
        for(Object child : children){
            dispatch(child,adapter);
            if(adapter.isPropagationStopped()) return;
        }
    }


    private void  checkInterfaces(Class<?> eventType, Map<Class<?>, ArrayList<HandlerHolder>> componentHandlers, ArrayList<HandlerHolder> interfaceHolderList, HashSet<Class<?>> visitedInterfaces)
    {
        if(visitedInterfaces.contains(eventType)){
            return;
        }
        visitedInterfaces.add(eventType);
        for(Class<?> iface : eventType.getInterfaces()){
            if(componentHandlers.containsKey(iface)){
                interfaceHolderList.addAll(componentHandlers.get(iface));
            }
            checkInterfaces(iface,componentHandlers, interfaceHolderList, visitedInterfaces);
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

        //ClassName, EventType, MethodSignature
        Map<String, HashMap<String, HashSet<MethodSignature>>> preCache = eventFile.methodCache;

        HashMap<String,Class<?>> foundClasses = new HashMap<>();

        //qualified class name is the class that contains the method
        for(String qualifiedClassName : preCache.keySet()){
            Class<?> currentClass = foundClasses.getOrDefault(qualifiedClassName,null);
            if(currentClass == null) {
                try {
                    currentClass = Class.forName(qualifiedClassName);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            HashMap<Class<?>, ArrayList<HandlerHolder>> cache = cachedMethods.computeIfAbsent(currentClass, k -> new HashMap<>());
            HashMap<String,HashSet<MethodSignature>> methodsToCache = preCache.get(qualifiedClassName);

            for(String qualifiedEventClassName : methodsToCache.keySet()){
                Class<?> eventType = foundClasses.getOrDefault(qualifiedClassName,null);
                if(eventType == null) {
                    try {
                        eventType = Class.forName(qualifiedEventClassName);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }

                ArrayList<HandlerHolder> methodsWithEvent = cache.computeIfAbsent(eventType, k->new ArrayList<>());

                //System.out.println("Main Class: " + qualifiedClassName + " Event Type: " + qualifiedEventClassName);
                //System.out.println(methodsToCache.keySet());
                for(MethodSignature sig : methodsToCache.get(qualifiedEventClassName)){

                    Class<?> originClass = foundClasses.getOrDefault(sig.originClass, null);
                    if(originClass == null){
                        try {
                            originClass = Class.forName(sig.originClass);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
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
                        method = originClass.getDeclaredMethod(methodName, parameterTypes);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                    method.setAccessible(true);
                    Class<?> adapterType = null;
                    if(sig.expectsAdapter){
                        adapterType = method.getParameters()[1].getType();
                    }
                    HandlerHolder hold = new HandlerHolder(method, sig.priority, sig.expectsAdapter,adapterType, sig.useSubClasses);
                    methodsWithEvent.add(hold);
                }

                Collections.sort(methodsWithEvent, new Comparator<HandlerHolder>() {
                    @Override
                    public int compare(HandlerHolder o1, HandlerHolder o2) {
                        return o2.priority-o1.priority;
                    }
                });
            }
        }
    }


    // Internal holder for a handler method and its priority
    private class HandlerHolder {
        final Method method;
        final int priority;
        final boolean expectsAdapter;
        final Class<?> adapterType;
        final boolean useSubClasses;

        HandlerHolder(Method method, int priority, boolean expectsAdapter,Class<?> adapterType, boolean useSubClasses) {
            this.method = method;
            this.priority = priority;
            this.adapterType = adapterType;
            this.expectsAdapter = expectsAdapter;
            this.useSubClasses = useSubClasses;
        }
    }
}
