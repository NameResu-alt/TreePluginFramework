package org.treepluginframework.EventSystem.ComponentArchitecture;

import org.treepluginframework.EventSubscription;
import org.treepluginframework.EventSystem.EventArchitecture.IEvent;

import java.lang.reflect.Method;
import java.util.*;

public class EventDispatcher {
    // Singleton instance
    private static final EventDispatcher INSTANCE = new EventDispatcher();

    public static EventDispatcher getInstance() {
        return INSTANCE;
    }

    // Internal holder for a handler method and its priority
    private static class HandlerHolder {
        final Method method;
        final int priority;
        final boolean expectsAdapter;

        HandlerHolder(Method method, int priority, boolean expectsAdapter) {
            this.method = method;
            this.priority = priority;
            this.expectsAdapter = expectsAdapter;
        }
    }

    private final List<Class<?>> acceptableEventTypes = new ArrayList<>();

    // Cache of handler holders per component class
    private final Map<Class<?>, Map<Class<?>, HandlerHolder>> handlerCache = new HashMap<>();

    // Subscriptions: instance -> its handler holders map
    private final Map<Object, Map<Class<?>, HandlerHolder>> subscriptions = new HashMap<>();

    // Parent and children relationships
    private final Map<Object, Object> parentMap = new HashMap<>();
    private final Map<Object, List<Object>> childrenMap = new HashMap<>();

    // Cache of sorted children per component and event type
    private final Map<Object, Map<Class<?>, List<Object>>> sortedChildrenCache = new HashMap<>();

    // Private constructor for singleton
    private EventDispatcher() {
        acceptableEventTypes.add(IEvent.class);
    }

    public void addAcceptableEventType(Class<?> eventType){
        this.acceptableEventTypes.add(eventType);
    }

    /**
     * Register a component and its parent, introspecting annotated handlers,
     * and update sorted children lists for event priority.
     */
    public void registerComponent(Object component, Object parent) {
        Class<?> clazz = component.getClass();

        // Retrieve or compute handler holders for this class
        Map<Class<?>, HandlerHolder> handlers = handlerCache.computeIfAbsent(clazz, cls -> {
            Map<Class<?>, HandlerHolder> map = new HashMap<>();
            for (Method method : cls.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EventSubscription.class)) {
                    Class<?>[] params = method.getParameterTypes();

                    boolean used = false;
                    boolean expectsAdapter = false;
                    if(params.length == 1)
                    {
                        used = true;
                        expectsAdapter = false;
                    } else if (params.length == 2 &&
                            EventAdapter.class.isAssignableFrom(params[1])) {
                        used = true;
                        expectsAdapter = true;
                    }

                    if(used){
                        System.out.println("Added a subscription for the event " + params[0].toString());
                        EventSubscription annotation = method.getAnnotation(EventSubscription.class);
                        int priority = annotation.priority();
                        method.setAccessible(true);
                        map.put(params[0], new HandlerHolder(method, priority,expectsAdapter));
                        break;
                    }
                    /*
                    for(Class<?> classType : acceptableEventTypes){
                        boolean expectsAdapter = false;
                        if(params.length == 1 && classType.isAssignableFrom(params[0]))
                        {
                            used = true;
                            expectsAdapter = false;
                        } else if (params.length == 2 &&
                                classType.isAssignableFrom(params[0]) &&
                                EventAdapter.class.isAssignableFrom(params[1])) {
                            used = true;
                            expectsAdapter = true;
                        }

                        if(used){
                            System.out.println("Added a subscription for the event " + classType.toString());
                            EventSubscription annotation = method.getAnnotation(EventSubscription.class);
                            int priority = annotation.priority();
                            method.setAccessible(true);
                            map.put(params[0], new HandlerHolder(method, priority,expectsAdapter));
                            break;
                        }
                    }
                    */


                    if(!used)
                    {
                        System.err.println("Invalid method signature for event handler: " + method +
                                " in class: " + cls.getName() + ". Accepted parameter types: " + Arrays.toString(params));
                    }
                }
            }
            return map;
        });

        // Associate this instance with its handlers
        subscriptions.put(component, handlers);

        // Set up parent-child relationship
        if (parent != null) {
            parentMap.put(component, parent);
            childrenMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(component);
            // Invalidate or update sortedChildrenCache for this parent. Will cause a recalculation.
            //This is good for multiple reads, not as great for multiple writes. But have to compromise.
            sortedChildrenCache.remove(parent);
        }
        childrenMap.putIfAbsent(component, new ArrayList<>());
        sortedChildrenCache.putIfAbsent(component, new HashMap<>());
    }

    /**
     * Unregister a component, removing it from all maps,
     * and update sorted children lists.
     */
    public void unregisterComponent(Object component) {
        subscriptions.remove(component);
        Object parent = parentMap.remove(component);
        if (parent != null) {
            List<Object> siblings = childrenMap.get(parent);
            if (siblings != null) {
                siblings.remove(component);
                sortedChildrenCache.remove(parent);
            }
        }
        childrenMap.remove(component);
        sortedChildrenCache.remove(component);
    }

    /**
     * Emit an event that's native to the code.
     */
    public void emit(Object fromComponent, IEvent event) {
        dispatch(fromComponent, new NativeEventAdapter(event));
    }

    /**
     * Emit an event that's not native to the code
     * @param fromComponent
     * @param adapter
     */
    public void emit(Object fromComponent, EventAdapter<?> adapter){dispatch(fromComponent,adapter);}

    /**
     * Internal dispatch logic (blocking, synchronous) with cached priority ordering.
     */
    private void dispatch(Object component, EventAdapter<?> adapter){

        if(component == null)
        {
            System.out.println("Attempted to dispatch an event with a null component: " + adapter.getEvent().getClass());
            return;
        }

        if(adapter.getDirection().equals("up"))
        {
            adapter.recordVisitedUpstream(component);
        }
        else if(adapter.getDirection().equals("down"))
        {
            adapter.recordVisistedDownStream(component);
        }

        //Once you find the endpoint, start going back down.
        if(adapter.getDirection().equals("up") && adapter.getEndPoint() == component.getClass()){
            adapter.setDirection("down");
        }

        // Propagate downstream or upstream
        if ("down".equals(adapter.getDirection())) {
            //Only execute the method if it's going downstream. Letting Components access things going upstream sounds like a good way to get big sphagetti.
            HandlerHolder holder = subscriptions.getOrDefault(component, Collections.emptyMap()).get(adapter.getEffectiveEventType());

            if(holder == null && subscriptions.containsKey(component)) {
                //This is to cover Subscriptions that only cover the base class of an object.
                //One example, subscribing to EntityDamageEvent, and getting a EntityDamageByEntityEvent.
                for (Map.Entry<Class<?>, HandlerHolder> entry : subscriptions.get(component).entrySet()) {
                    if (entry.getKey().isAssignableFrom(adapter.getEffectiveEventType())) {
                        //TODO: Add a new subscription with that class, so that I don't have to recompute it again
                        holder = entry.getValue();
                        break;
                    }
                }
            }

            if(holder != null)
            {
                try {
                    if(holder.expectsAdapter)
                    {
                        holder.method.invoke(component, adapter.getEvent(), adapter);
                    }
                    else
                    {
                        holder.method.invoke(component, adapter.getEvent());
                    }

                    if (adapter.isPropagationStopped()) return;
                } catch(Exception e){
                    e.printStackTrace();
                }
            }

            List<Object> sortedChildren = getSortedChildren(component, adapter.getEffectiveEventType());
            for (Object child : sortedChildren) {
                dispatch(child, adapter);
                if (adapter.isPropagationStopped()) {
                    break;
                }
            }
        } else if ("up".equals(adapter.getDirection())) {
            Object parent = parentMap.get(component);
            if (parent != null) {
                dispatch(parent, adapter);
            }
        }

    }


    /**
     * Retrieve or compute sorted children list for a specific event type.
     */
    private List<Object> getSortedChildren(Object component, Class<?> eventType) {
        Map<Class<?>, List<Object>> cacheForComponent = sortedChildrenCache.computeIfAbsent(component, k -> new HashMap<>());
        return cacheForComponent.computeIfAbsent(eventType, et -> {
            List<Object> children = new ArrayList<>(childrenMap.getOrDefault(component, Collections.emptyList()));
            children.sort((c1, c2) -> {
                int p1 = Optional.ofNullable(subscriptions.get(c1))
                        .map(m -> m.get(eventType))
                        .map(h -> h.priority)
                        .orElse(0);
                int p2 = Optional.ofNullable(subscriptions.get(c2))
                        .map(m -> m.get(eventType))
                        .map(h -> h.priority)
                        .orElse(0);
                return Integer.compare(p2, p1); // descending
            });
            return children;
        });
    }

    public void log(){
        System.out.println("Handler Cache: " + handlerCache.size());
        System.out.println("Subscriptions: " + subscriptions.size());
        System.out.println("Parent Map: " + parentMap.size());
        System.out.println("Children Map: " + childrenMap.size());
        System.out.println("Sorted Children Cache: " + sortedChildrenCache.size());
    }
}
