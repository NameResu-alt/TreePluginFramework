package org.treepluginframework.component_architecture;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.events.EventAdapter;
import org.treepluginframework.events.IEvent;
import org.treepluginframework.events.NativeEventAdapter;
import org.treepluginframework.meta_events.TPFMetaEvent;
import org.treepluginframework.meta_events.dag.TPFDAGEdgeAdditionMetaEvent;
import org.treepluginframework.meta_events.dag.TPFDAGEdgeDeletionMetaEvent;
import org.treepluginframework.meta_events.dag.TPFDAGNodeAdditionMetaEvent;
import org.treepluginframework.meta_events.dag.TPFDAGNodeDeletionMetaEvent;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeMetadataCache;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeReference;
import org.treepluginframework.meta_events.event_propagation.TPFEventPropagationCompletedMetaEvent;
import org.treepluginframework.meta_events.event_propagation.TPFEventPropagationStartedMetaEvent;
import org.treepluginframework.meta_events.event_propagation.TPFEventPropagationTransferEvent;
import org.treepluginframework.meta_events.structure.*;
import org.treepluginframework.values.ConstructorInformation;
import org.treepluginframework.values.MethodSignature;
import org.treepluginframework.values.TPFEventFile;
import org.treepluginframework.values.TPFStructureFile;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class TPFEventDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(TPFEventDispatcher.class);

    private final TPF tpf;

    private final DAG<Object> graph = new DAG<>(DAG.Mode.IDENTITY);//DAG.identity();//new DAG<Object>();
    //Class type, event type, HandlerHolder.
    //One event type can have multiple HandlerHolders, if whatever the eventtype was an interface.
    //Class, EventType, HandlerHolders of Event type.
    private HashMap<Class<?>, HashMap<Class<?>, ArrayList<HandlerHolder>>> cachedMethods = new HashMap<>();
    //The class of the object that has the methods.
    //The list of classes that have already been resolved.
    //This is for anonymous classes.
    private IdentityHashMap<Object, HashMap<Class<?>, ArrayList<HandlerHolder>>> mergedHandlerCache = new IdentityHashMap<>();

    private final List<Object[]> queuedObjects = new ArrayList<>();

    private final TPFEventFile eventFile;
    private final TPFStructureFile metaFile;

    private final TPFNodeRepository nodeRepository;

    private boolean dispatchOnGoing = false;

    private final MetaEventAsyncDispatcher metaEventDispatcher = new MetaEventAsyncDispatcher();

    private boolean startup = false;

    public TPFEventDispatcher(TPFStructureFile metaFile, TPFEventFile eventFile, TPFNodeRepository nodeRepository, TPF tpf){
        this.metaFile = metaFile;
        this.eventFile = eventFile;
        this.nodeRepository = nodeRepository;
        this.tpf = tpf;

        //NOTE: I need to keep track of when its a structure node, to send the right meta event.
        //It's fine for now.
        graph.addListener(new DAG.DAGListener<Object>() {
            @Override
            public void onEvent(DAG.DAGEvent<Object> event, UUID dagUUID, long version) {
                //DAGNodeReference from = DAGNodeMetadataCache.getNodeReference(graph.getNodeUUID(edge.from), edge.from);
                if(event instanceof DAG.NodeAdded<Object> nodeAdded){
                    DAGNodeDetails nodeDetails = DAGNodeMetadataCache.getNodeDetails(graph.getNodeUUID(nodeAdded.node()),nodeAdded.node());

                    if(!startup){
                        TPFDAGNodeAdditionMetaEvent addMeta = new TPFDAGNodeAdditionMetaEvent(tpf.getTpfUUID(),"Added Node",dagUUID,version, nodeDetails);
                        emitMetaEvent(addMeta);
                    }
                    else
                    {
                        //Expected dependencies, and injected dependencies.
                        LinkedHashMap<String, ConstructorInformation> constructorInformation = metaFile.constructorInformation;

                        //This feels like this gets me in trouble later with inner classes.
                        ///TODO: account for inner classes.
                        ConstructorInformation info = constructorInformation.get(nodeDetails.getObjectClass().getCanonicalName());

                        //The actual constructor that was used.
                        List<String> parametersExpected = new ArrayList<>(info.neededConstructorParameters);
                        //The desired ones.
                        List<String> parametersDesired = new ArrayList<>(info.desiredConstructorParameters);

                        TPFStructureNodeAddedEvent structureAddMeta = new TPFStructureNodeAddedEvent(tpf.getTpfUUID(),  "Added Node", dagUUID, version, nodeDetails, parametersExpected, parametersDesired);
                        emitMetaEvent(structureAddMeta);
                    }

                }
                if(event instanceof DAG.NodeRemoved<Object> nodeRemoved){
                    //
                    DAGNodeReference nodeReference = DAGNodeMetadataCache.getNodeReference(nodeRemoved.nodeRemovedUUID(),nodeRemoved.node());
                    TPFDAGNodeDeletionMetaEvent removeMeta = new TPFDAGNodeDeletionMetaEvent(tpf.getTpfUUID(), "Removed Node", dagUUID, version, nodeReference);
                    emitMetaEvent(removeMeta);
                }
                if(event instanceof DAG.EdgeAdded<Object> edgeAdded){
                    if(!startup){
                        DAGNodeDetails from = (edgeAdded.from() != null) ? DAGNodeMetadataCache.getNodeDetails(graph.getNodeUUID(edgeAdded.from()),edgeAdded.from()) : null;
                        DAGNodeDetails to = DAGNodeMetadataCache.getNodeDetails(graph.getNodeUUID(edgeAdded.to()), edgeAdded.to());
                        TPFDAGEdgeAdditionMetaEvent addMeta = new TPFDAGEdgeAdditionMetaEvent(tpf.getTpfUUID(), "Added Edge", dagUUID, version, from, to);
                        emitMetaEvent(addMeta);
                    }
                    else
                    {
                        Object from = edgeAdded.from();
                        Object to = edgeAdded.to();

                        DAGNodeReference fromReference = (from != null) ? DAGNodeMetadataCache.getNodeReference(graph.getNodeUUID(from),from) : null;
                        DAGNodeDetails toDetails = DAGNodeMetadataCache.getNodeDetails(graph.getNodeUUID(to),to);

                        LinkedHashMap<String, ConstructorInformation> constructorInformation = metaFile.constructorInformation;

                        ///TODO: account for inner classes.
                        ConstructorInformation info = constructorInformation.get(to.getClass().getCanonicalName());

                        //The actual constructor that was used.
                        List<String> parametersExpected = new ArrayList<>(info.neededConstructorParameters);
                        //The desired ones.
                        List<String> parametersDesired = new ArrayList<>(info.desiredConstructorParameters);

                        TPFStructureEdgeAddedEvent edgeAddedMeta = new TPFStructureEdgeAddedEvent(tpf.getTpfUUID(), "Edge Added Structure", dagUUID, version, fromReference, toDetails, parametersExpected, parametersDesired);
                        emitMetaEvent(edgeAddedMeta);
                    }
                }
                if(event instanceof DAG.EdgeRemoved<Object> edgeRemoved){
                    DAGNodeReference nodeReferenceFrom = DAGNodeMetadataCache.getNodeReference(edgeRemoved.fromUUID(), edgeRemoved.from());
                    DAGNodeReference nodeReferenceTo = DAGNodeMetadataCache.getNodeReference(edgeRemoved.toUUID(), edgeRemoved.to());
                    TPFDAGEdgeDeletionMetaEvent deleteMeta = new TPFDAGEdgeDeletionMetaEvent(tpf.getTpfUUID(), "Removed Edge", dagUUID, version, nodeReferenceFrom,nodeReferenceTo);
                    emitMetaEvent(deleteMeta);
                }
            }
        });

        calculateCachedMethods();
    }

    public enum RegisterResult{
        ADDED, ALREADY_EXISTS, QUEUED
    }

    public RegisterResult register(Object parent, Object component, boolean afterCurrentEvent){
        //if the graph doesn't contain the parent, nor the component.
        //I need to see if parent or component is an anonymous inner class.
        //If that's the case, I need to do a runtime cache of its HandlerHolders.
        RegisterResult result = RegisterResult.ADDED;
        if(!afterCurrentEvent)
        {
            boolean ableToAddEdge = graph.addEdge(parent, component);
            if(!ableToAddEdge){
                result = RegisterResult.ALREADY_EXISTS;
            }
        }
        else
        {
            queuedObjects.add(new Object[]{parent,component});
        }

        if(component != null){
            if(component.getClass().isAnonymousClass()){
                runtimeRegister(component);
            }
        }

        return result;
    }

    private void runtimeRegister(Object obj){
        if(obj == null) return;


        HashMap<String, Class<?>> foundClasses = new HashMap<>();


        Class<?> anonClass = obj.getClass();

        HashMap<Class<?>,ArrayList<HandlerHolder>> anonymousCache =  new HashMap<>();

        try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
            ClassInfo classInfo = scanResult.getClassInfo(anonClass.getName());

            // Collect method signatures from superclasses and interfaces
            Set<String> parentMethods = new HashSet<>();
            classInfo.getSuperclasses().forEach(s -> {
                try {
                    for (Method m : Class.forName(s.getName()).getDeclaredMethods()) {
                        parentMethods.add(signature(m));
                    }
                } catch (ClassNotFoundException ignored) {}
            });
            classInfo.getInterfaces().forEach(i -> {
                try {
                    for (Method m : Class.forName(i.getName()).getDeclaredMethods()) {
                        parentMethods.add(signature(m));
                    }
                } catch (ClassNotFoundException ignored) {}
            });

            // Now check declared methods in the anonymous class
            /// TODO: Add the MetaEventSubscription to runtimeRegister.
            for (Method m : anonClass.getDeclaredMethods()) {
                EventSubscription eventSubscription = m.getAnnotation(EventSubscription.class);
                if(eventSubscription == null) continue;

                if(!validateEventMethodSignature(m)){
                    continue;
                }

                boolean expectsAdapter = m.getParameters().length == 2;
                Class<?> eventType = m.getParameters()[0].getType();
                Class<?> adapterType = (expectsAdapter) ? m.getParameters()[1].getType() : null;

                m.setAccessible(true);
                HandlerHolder h = new HandlerHolder(m, eventSubscription.priority(),expectsAdapter, adapterType, eventSubscription.useSubClasses());

                if (eventSubscription.useSubClasses()) {
                    addSubClasses(eventType,h,anonymousCache,foundClasses,scanResult);
                }

                anonymousCache.computeIfAbsent(eventType, k->new ArrayList<>()).add(h);

            }
        }

        HashMap<Class<?>,ArrayList<HandlerHolder>> combined = mergedHandlerCache.compute(obj, (k,v)-> new HashMap<>());

        Class<?> baseSuperClass = anonClass.getSuperclass();
        if(cachedMethods.containsKey(baseSuperClass)){
            System.out.println("Cached method already has this class: " + baseSuperClass);
            HashMap<Class<?>, ArrayList<HandlerHolder>> handlersOfBaseClass = cachedMethods.get(baseSuperClass);
            for(Class<?> eventType : handlersOfBaseClass.keySet()){

                ArrayList<HandlerHolder> combine = new ArrayList<>(handlersOfBaseClass.get(eventType));
                if(anonymousCache.containsKey(eventType)){
                    ArrayList<HandlerHolder> anonymousHandlersOfEvent = anonymousCache.get(eventType);
                    System.out.println("Cobined size before: " + combine.size());
                    combine.removeAll(anonymousHandlersOfEvent);
                    System.out.println("Cobined size after remove: " + combine.size());
                    combine.addAll(anonymousHandlersOfEvent);
                    System.out.println("Conbined final size: " + combine.size());
                }
                combined.put(eventType,combine);
            }
        }
        else
        {
            System.out.println("Cache doesn't hold the base class " + baseSuperClass);
        }

        for(Class<?> eventType : anonymousCache.keySet()){
            if(combined.containsKey(eventType)) continue;
            combined.put(eventType, anonymousCache.get(eventType));
        }

        System.out.println("Combined Size: " + combined.size());
        System.out.println("KeySet: " + combined.keySet());
        for(Class<?> keyClass : combined.keySet()){
            ArrayList<HandlerHolder> list = combined.get(keyClass);
            list.sort((o1, o2) -> o2.priority - o1.priority);
            System.out.println("Anonymous event class: " + keyClass);
            list.forEach(e->System.out.println("\tMethod: " + e));
        }
    }

    public void addMetaEventListener(Object listener){
        this.metaEventDispatcher.addListener(listener);
    }

    private boolean validateEventMethodSignature(Method m){
        if(m.getParameters().length > 3){
            throw new IllegalArgumentException("An EventSubscription method can only have an event, and its adapter as parameters");
        }

        Parameter[] parameters = m.getParameters();
        Class<?> eventType = parameters[0].getType();
        Class<?> adapterType = (parameters.length > 1) ? parameters[1].getType() : Void.class;

        if(eventType.isPrimitive()){
            //Problem.

            throw new IllegalArgumentException("An EventSubscription method cannot have a primitive as an event.");
        }

        if(isIterableLike(eventType))
        {
            throw new IllegalArgumentException("An EventSubscription method must have an event that's not a Map, Array, or Iterable in any way.");
        }

        if(adapterType != Void.class){
            if(!EventAdapter.class.isAssignableFrom(adapterType)){
                throw new IllegalArgumentException("Second argument must be an EventAdapter");
            }

            Type superType = adapterType.getGenericSuperclass();

            if(superType instanceof ParameterizedType pt){
                Type actual = pt.getActualTypeArguments()[0];
                if(actual instanceof Class<?> adapterEventType){
                    if(!adapterEventType.isAssignableFrom(eventType)){
                        throw new IllegalArgumentException(
                                "The EventAdapter's generic type must be compatible with the Event type"
                        );
                    }
                }
            }
        }

        return true;
    }

    private boolean isIterableLike(Class<?> clazz) {
        if (clazz == null) return false;

        return Iterable.class.isAssignableFrom(clazz) ||
                Map.class.isAssignableFrom(clazz) ||
                clazz.isArray() ||
                CharSequence.class.isAssignableFrom(clazz) ||
                Iterator.class.isAssignableFrom(clazz) ||
                Enumeration.class.isAssignableFrom(clazz) ||
                Stream.class.isAssignableFrom(clazz) ||
                Spliterator.class.isAssignableFrom(clazz);
    }

    private  String signature(Method m) {
        return m.getName() + Arrays.toString(m.getParameterTypes());
    }

    public void unregister(Object obj){
        graph.removeNode(obj);
        mergedHandlerCache.remove(obj);
    }

    // For events that implement IEvent — wraps them in a NativeEventAdapter
    public void emit(Object fromComponent, IEvent event){
        if (event == null) throw new IllegalArgumentException("Event cannot be null");
        emit(fromComponent,new NativeEventAdapter(event));
        //dispatchOnGoing = true;
        //dispatch(fromComponent, new NativeEventAdapter(event));
        //finishesdDispatch();
    }

    // For external or generic events — assumes a custom adapter is already provided
    public void emit(Object fromComponent, EventAdapter<?> adapter){
        if (adapter == null) throw new IllegalArgumentException("Adapter cannot be null");
        if (fromComponent == null) throw new NullPointerException("Cannot emit an event from a null");
        dispatchOnGoing = true;
        //dispatch(fromComponent, adapter);

        DAGNodeReference startingNode = DAGNodeMetadataCache.getNodeReference(graph.getNodeUUID(fromComponent), fromComponent);
        emitMetaEvent(new TPFEventPropagationStartedMetaEvent(tpf.getTpfUUID(),"",graph.getDagUUID(),graph.getVersion(),adapter.getEventUUID(), adapter.getEffectiveEventType(), startingNode));

        Deque<PropagationEdge> stack = new ArrayDeque<>();
        stack.push(new PropagationEdge(null, fromComponent));

        while(!stack.isEmpty() && !adapter.isPropagationStopped()){
            PropagationEdge edge = stack.pop();

            Object current = edge.getTo();
            adapter.recordVisistedDownStream(current);

            //Reference of the nodes from and to.
            DAGNodeReference from = DAGNodeMetadataCache.getNodeReference(graph.getNodeUUID(edge.from), edge.from);
            DAGNodeReference to = DAGNodeMetadataCache.getNodeReference(graph.getNodeUUID(edge.to), edge.to);

            emitMetaEvent(new TPFEventPropagationTransferEvent(tpf.getTpfUUID(),"",graph.getDagUUID(),graph.getVersion(),adapter.getEventUUID(), adapter.getEffectiveEventType(),from,to));

            invokeHandlers(current,adapter, false);

            if(adapter.isPropagationStopped()){
                break;
            }

            List<Object> children = new ArrayList<>(graph.getChildren(current));
            //This reverse is tricky, since the way that the children are ordered makes sense. Leftmost element comes first, rightmost comes last.
            //Problem with the stack datastructure is that it works the other way around. Leftmost comes last, and Rightmost is popped first.
            //To fix this, I need to reverse the children first.
            Collections.reverse(children);
            for(Object child : children){
                //I may need a previous, because when its popped,
                //I need to record that eventpropagation happend from, to
                //I can't just record it right here.
                stack.push(new PropagationEdge(current,child));
            }
        }

        LinkedHashSet<UUID> visited = new LinkedHashSet<>();
        adapter.getVisitedComponentsDownstream().forEach(k->visited.add(graph.getNodeUUID(k)));

        emitMetaEvent(new TPFEventPropagationCompletedMetaEvent(tpf.getTpfUUID(),"",graph.getDagUUID(),graph.getVersion(),adapter.getEventUUID(), adapter.getEffectiveEventType(), adapter.isPropagationStopped(), visited));

        finishedDispatch(adapter);
    }

    void emitMetaEvent(TPFMetaEvent<?> metaEvent){
        this.metaEventDispatcher.dispatchMetaEvent(metaEvent);
    }

    private void invokeHandlers(Object component, EventAdapter<?> adapter, boolean isAsync){
        Class<?> componentClass = component.getClass();
        Class<?> eventType = adapter.getEffectiveEventType();
        ArrayList<HandlerHolder> handlers;

        if(mergedHandlerCache.containsKey(component)){

            if(mergedHandlerCache.get(component).containsKey(eventType)){
                handlers = mergedHandlerCache.get(component).get(eventType);
            }
            else
            {
                return;
            }
        }
        else if(cachedMethods.containsKey(componentClass)){
            if(cachedMethods.get(componentClass).containsKey(eventType)){
                handlers = cachedMethods.get(componentClass).get(eventType);
            }
            else
            {
                return;
            }
        }
        else
        {
            return;
        }

        if(handlers == null || handlers.isEmpty()) {
            return;
        }

        for(HandlerHolder handler : handlers){
            try {
                if (handler.expectsAdapter) {
                    /// TODO: Log that the event was skipped due to adapter mismatch

                    Class<?> adapterType = (adapter.getClass().isAnonymousClass()) ? adapter.getClass().getSuperclass() : adapter.getClass();
                    if(adapterType.isAssignableFrom(handler.adapterType)) {
                        handler.method.invoke(component, adapter.getEvent(), adapter);
                    }
                    else
                    {
                        System.out.println("Adapter not compatible, Adapter is " + adapter.getClass() + " And Handler wants: " + handler.adapterType);
                    }
                } else {
                    handler.method.invoke(component, adapter.getEvent());
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                System.out.println("ERROR Occurred invoking handler for class: " + componentClass.getCanonicalName() + " For Event Type: " + eventType.getCanonicalName());
                System.out.println("Method Error Occurred in: " + handler.method.getName());
                 System.out.println("Cause: " + e.getCause());
                 System.out.println("Stack Trace: " + Arrays.toString(e.getStackTrace()));

                //logger.error("Async error stacktrace: " + Arrays.toString(e.getStackTrace()) + " Event Type: " + );
                throw new RuntimeException(e);
            }
        }
    }

    private void finishedDispatch(EventAdapter<?> adapter){
        this.dispatchOnGoing = false;
        for(Object[] o : queuedObjects)
        {
            this.register(o[0],o[1],false);
        }

        this.queuedObjects.clear();
    }



    private void checkInterfaces(Class<?> eventType, Map<Class<?>, ArrayList<HandlerHolder>> componentHandlers, ArrayList<HandlerHolder> interfaceHolderList, HashSet<Class<?>> visitedInterfaces)
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
        startup = true;
        if(metaFile == null){
            startup = false;
            return;
        }

        TPFStructureStartMetaEvent startMetaEvent = new TPFStructureStartMetaEvent(tpf.getTpfUUID(),"DAG Start", graph.getDagUUID());
        emitMetaEvent(startMetaEvent);

        //Keep track of the node details?
        //I should keep it in the order that it was generated.
        //So thats why its up here.
        List<DAGNodeDetails> nodeDetails = new ArrayList<>();


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

            nodeDetails.add(DAGNodeMetadataCache.getNodeDetails(graph.getNodeUUID(correspondingNode),correspondingNode));
        }

        startup = false;

        TPFStructureCompletedMetaEvent completedMetaEvent = new TPFStructureCompletedMetaEvent(tpf.getTpfUUID(), "Structure Completed", graph.getDagUUID(), graph.getVersion(), nodeDetails);
        emitMetaEvent(completedMetaEvent);
        //graph.printGraph();
    }

    private void calculateCachedMethods() {
        if (eventFile == null) return;

        // ClassName, EventType, MethodSignature
        Map<String, HashMap<String, HashSet<MethodSignature>>> preCache = eventFile.methodCache;

        HashMap<String, Class<?>> foundClasses = new HashMap<>();

        // Scan once up front
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().scan()) {

            // qualified class name is the class that contains the method
            for (String qualifiedClassName : preCache.keySet()) {
                Class<?> currentClass = resolveClass(qualifiedClassName,foundClasses);//foundClasses.get(qualifiedClassName);

                HashMap<Class<?>, ArrayList<HandlerHolder>> cache =
                        cachedMethods.computeIfAbsent(currentClass, k -> new HashMap<>());
                HashMap<String, HashSet<MethodSignature>> methodsToCache = preCache.get(qualifiedClassName);

                for (String qualifiedEventClassName : methodsToCache.keySet()) {
                    Class<?> eventType = resolveClass(qualifiedEventClassName,foundClasses);//foundClasses.get(qualifiedEventClassName);

                    ArrayList<HandlerHolder> methodsWithEvent =
                            cache.computeIfAbsent(eventType, k -> new ArrayList<>());

                    for (MethodSignature sig : methodsToCache.get(qualifiedEventClassName)) {

                        Class<?> originClass = resolveClass(sig.originClass,foundClasses);//foundClasses.get(sig.originClass);

                        Class<?>[] parameterTypes = new Class[sig.parameterTypes.size()];
                        for (int i = 0; i < sig.parameterTypes.size(); i++) {
                            String parameterType = sig.parameterTypes.get(i);
                            Class<?> paramClass = resolveClass(parameterType,foundClasses);//foundClasses.get(parameterType);

                            parameterTypes[i] = paramClass;
                        }

                        Method method;
                        try {
                            method = originClass.getDeclaredMethod(sig.methodName, parameterTypes);
                        } catch (NoSuchMethodException e) {
                            throw new RuntimeException(e);
                        }
                        method.setAccessible(true);

                        Class<?> adapterType = null;
                        if (sig.expectsAdapter) {
                            adapterType = method.getParameters()[1].getType();
                        }

                        HandlerHolder hold = new HandlerHolder(
                                method,
                                sig.priority,
                                sig.expectsAdapter,
                                adapterType,
                                sig.useSubClasses
                        );

                        TPFStructureEventSubscriptionRegisteredEvent register = new TPFStructureEventSubscriptionRegisteredEvent(tpf.getTpfUUID(),"Subscription register", currentClass, originClass, eventType, method.getName(), adapterType);
                        emitMetaEvent(register);

                        if (sig.useSubClasses) {
                            List<Class<?>> descendantEventTypes = addSubClasses(eventType,hold,cache,foundClasses,scanResult);
                            for(Class<?> descendant : descendantEventTypes){
                                TPFStructureEventSubscriptionRegisteredEvent descendantRegister = new TPFStructureEventSubscriptionRegisteredEvent(tpf.getTpfUUID(), "Subscription descendant register", currentClass, originClass, descendant, method.getName(), adapterType);
                                emitMetaEvent(descendantRegister);
                            }
                        }

                        methodsWithEvent.add(hold);
                    }
                }

                // sort each handler list by priority once per event type
                for (ArrayList<HandlerHolder> list : cache.values()) {
                    list.sort((o1, o2) -> o2.priority - o1.priority);
                }
            }
        }
    }

    private Class<?> resolveClass(String className, Map<String, Class<?>> cache) {
        return cache.computeIfAbsent(className, name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    //Add subclasses means that the subscription isn't looking for that type specifically.
    //It's looking for any descendants as well.
    private List<Class<?>> addSubClasses(Class<?> eventType, HandlerHolder handler,
                               HashMap<Class<?>, ArrayList<HandlerHolder>> cache,
                               Map<String, Class<?>> classCache,
                               ScanResult scanResult) {
        ClassInfoList associated = eventType.isInterface()
                ? scanResult.getClassesImplementing(eventType)
                : scanResult.getSubclasses(eventType);

        List<Class<?>> descendants = new ArrayList<>();

        for (ClassInfo ci : associated) {
            Class<?> descendant = resolveClass(ci.getName(), classCache);
            //Just in case, I don't want the descendant to put in information to the parent. I don't think
            //this if statement would ever be false though.
            if (descendant != eventType) {
                cache.computeIfAbsent(descendant, k -> new ArrayList<>()).add(handler);
                descendants.add(descendant);
            }
        }

        return descendants;
    }

    private class MetaEventAsyncDispatcher{
        //All meta event listeners get their own thread, so that they can't mess with each other
        private final List<Object> listeners = new ArrayList<>();
        private final List<ExecutorService> listenerThreads = new ArrayList<>();

        public void addListener(Object listener){
            this.listeners.add(listener);
            this.listenerThreads.add(Executors.newSingleThreadExecutor());
        }

        //I should make MetaEvents cloneable, since I don't want listeners to
        //be able to mess things up for listeners further down the line.
        //Then, anything that you chance is good.
        public void dispatchMetaEvent(TPFMetaEvent<?> metaEvent){

            //Event UUID is the same for all listeners, just in case.
            UUID eventUUID = UUID.randomUUID();
            for(int i = 0; i<listeners.size();i++){
                EventAdapter<TPFMetaEvent<?>> metaEventAdapter = new EventAdapter<>(metaEvent,eventUUID) {};
                Object listener = listeners.get(i);
                ExecutorService service = listenerThreads.get(i);

                service.submit(()->{
                    invokeHandlers(listener, metaEventAdapter, true);
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

        @Override
        public boolean equals(Object other){
            if(!(other instanceof HandlerHolder oHandler)){
                return false;
            }

            if(oHandler.method == this.method) return true;

            if(!oHandler.method.getName().equals(method.getName())){
                return false;
            }

            if (!Arrays.equals(oHandler.method.getParameterTypes(), method.getParameterTypes())) {
                return false;
            }

            int m1 = method.getModifiers();
            int m2 = oHandler.method.getModifiers();

            if (Modifier.isPublic(m1) || Modifier.isPublic(m2)) {
                // both must be public
                return Modifier.isPublic(m1) && Modifier.isPublic(m2);
            }
            if (Modifier.isProtected(m1) || Modifier.isProtected(m2)) {
                // both must be protected or package-private (can't mix public and protected in a weird way)
                return !Modifier.isPrivate(m1) && !Modifier.isPrivate(m2);
            }
// otherwise package-private (default)
            return !Modifier.isPrivate(m1) && !Modifier.isPrivate(m2);
        }

        @Override
        public int hashCode() {
            int visibility;
            if (Modifier.isPublic(method.getModifiers())) {
                visibility = 3;
            } else if (Modifier.isProtected(method.getModifiers())) {
                visibility = 2;
            } else if (Modifier.isPrivate(method.getModifiers())) {
                visibility = 0;
            } else {
                visibility = 1; // package-private
            }

            return Objects.hash(
                    method.getName(),
                    Arrays.hashCode(method.getParameterTypes()),
                    visibility
            );
        }

        @Override
        public String toString(){
            return method.getName();
        }
    }

    public DAGSnapshot getDAGSnapshot(){
        return new DAGSnapshot(graph);
    }

    public UUID getDAGUUID(){
        return this.graph.getDagUUID();
    }

    public void printDAG(){
        this.graph.printGraph();
    }

    public static class DAGSnapshot{
        private HashMap<UUID, DAGNodeDetails> nodes = new HashMap<>();
        private List<UUID> roots = new ArrayList<>();
        private Map<UUID, LinkedHashSet<UUID>> edges = new HashMap<>();
        private UUID dagUUID;
        private long version;

        public DAGSnapshot(DAG<?> dag){
            this.dagUUID = dag.getDagUUID();
            this.version = dag.getVersion();
            this.grabDAGData(dag);
        }

        private void grabDAGData(DAG<?> dag){
            if(dag.isEmpty()) return;

            this.roots = dag.getRootUUIDs();
            this.edges = dag.getAllEdgesInUUID();

            HashMap<UUID, ?> allNodes = dag.getAllNodes();
            for(UUID uuid : allNodes.keySet()){
                Object node = allNodes.get(uuid);
                DAGNodeDetails meta = DAGNodeMetadataCache.getNodeDetails(uuid,node);
                this.nodes.put(uuid, meta);
            }
        }

        public HashMap<UUID, DAGNodeDetails> getNodes() {
            return nodes;
        }

        public List<UUID> getRoots() {
            return roots;
        }

        public Map<UUID, LinkedHashSet<UUID>> getEdges() {
            return edges;
        }

        public UUID getDagUUID(){return dagUUID;}

        public long getVersion(){return this.version;}
    }

    private class PropagationEdge {
        private final Object from;
        private final Object to;

        public PropagationEdge(Object from, Object to) {
            this.from = from;
            this.to = Objects.requireNonNull(to, "to cannot be null");
        }

        public Object getFrom() {
            return from;
        }

        public Object getTo() {
            return to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PropagationEdge edge)) return false;
            return from.equals(edge.from) && to.equals(edge.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }

        @Override
        public String toString() {
            return "PropagationEdge{" +
                    "from=" + from +
                    ", to=" + to +
                    '}';
        }
    }
}
