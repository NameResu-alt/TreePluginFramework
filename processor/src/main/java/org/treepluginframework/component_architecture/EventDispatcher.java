package org.treepluginframework.component_architecture;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.events.EventAdapter;
import org.treepluginframework.events.IEvent;
import org.treepluginframework.events.NativeEventAdapter;

import java.lang.reflect.Method;
import java.util.*;

public class EventDispatcher {

    private final Map<Class<?>, Map<Class<?>, HandlerHolder>>  reflectionMethodCache = new HashMap<>();

    //Subscriptions: instance -> its handler holders map
    private final Map<Object,Map<Class<?>, HandlerHolder>> subscriptions = new HashMap<>();

    private final OneToManyBiMap<Object,Object> relationshipMap = new OneToManyBiMap<>();


    public void registerComponent(Object component, Object parent){
        Class<?> clazz = component.getClass();
        Map<Class<?>,HandlerHolder> reflectionCache = reflectionMethodCache.computeIfAbsent(clazz, cls ->{
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

        subscriptions.put(component,reflectionCache);
        relationshipMap.put(parent,component);
    }

    public void unregisterComponent(Object component){
        subscriptions.remove(component);
        relationshipMap.removeCompletely(component);
    }

    /***
     * Emit an event that's native to the code
     * @param fromComponent
     * @param event
     */
    public void emit(Object fromComponent, IEvent event){dispatch(fromComponent, new NativeEventAdapter(event));}

    /***
     * Emit an event that's not native to the code
     * @param fromComponent
     * @param adapter
     */
    public void emit(Object fromComponent, EventAdapter<?> adapter){dispatch(fromComponent,adapter);}
    
    
    private void dispatch(Object component, EventAdapter<?> adapter){
        if(component == null){
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

            List<Object> children = relationshipMap.getChildrenOfParent(component);
            Collections.sort(children, new Comparator<Object>() {
                @Override
                public int compare(Object o1, Object o2) {
                    int o1Priority = (!subscriptions.containsKey(o1)) ? 0 : subscriptions.get(o1).get(adapter.getEffectiveEventType()).priority;
                    int o2Priority = (!subscriptions.containsKey(o2)) ? 0 : subscriptions.get(o2).get(adapter.getEffectiveEventType()).priority;
                    return o1Priority - o2Priority;
                }
            });

            for (Object child : children) {
                dispatch(child, adapter);
                if (adapter.isPropagationStopped()) {
                    break;
                }
            }
        } else if ("up".equals(adapter.getDirection())) {
            Object parent = relationshipMap.getParentOfChild(component);
            if (parent != null) {
                dispatch(parent, adapter);
            }
        }
        
    }
    
    private class HandlerHolder{
        final Method method;
        final int priority;
        final boolean expectsAdapter;

        HandlerHolder(Method method, int priority, boolean expectsAdapter){
            this.method = method;
            this.priority = priority;
            this.expectsAdapter = expectsAdapter;
        }
    }
}
