package org.treepluginframework.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class EventAdapter<T> {
    private final T event;
    private boolean propagationStopped = false;
    private String direction = "down"; // or "up"
    private final List<Object> visitedComponentsUpstream = new ArrayList<>();
    private final List<Object> visitedComponentsDownstream = new ArrayList<>();
    private Class<?> endpoint;


    public EventAdapter(T event) {
        this.event = event;
    }

    public EventAdapter(T event, Class<?> endpoint){this.event = event; this.endpoint = endpoint;}

    public T getEvent() {
        return this.event;
    }

    public Class<?> getEndPoint(){
        return this.endpoint;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean isPropagationStopped() {
        return propagationStopped;
    }

    public void stopPropagation() {
        this.propagationStopped = true;
    }

    public void recordVisitedUpstream(Object component) {
        visitedComponentsUpstream.add(component);
    }

    public void recordVisistedDownStream(Object component){
        visitedComponentsDownstream.add(component);
    }

    public List<Object> getVisitedUpstreamComponents() {
        return Collections.unmodifiableList(visitedComponentsUpstream);
    }

    public List<Object> getVisitedDownstreamComponents(){
        return Collections.unmodifiableList(visitedComponentsDownstream);
    }

    public Class<?> getEffectiveEventType(){
        return getEvent().getClass();
    }
}
