package org.treepluginframework.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class EventAdapter<T> {
    private final T event;
    private final UUID eventUUID;
    private boolean propagationStopped = false;
    private final List<Object> visitedComponentsDownstream = new ArrayList<>();



    public EventAdapter(T event) {
        this.event = event;
        this.eventUUID = UUID.randomUUID();
    }
    public EventAdapter(T event, UUID eventUUID){
        this.event = event;
        this.eventUUID = eventUUID;
    }

    public UUID getEventUUID(){return this.eventUUID;}

    public T getEvent() {
        return this.event;
    }

    public boolean isPropagationStopped() {
        return propagationStopped;
    }

    public void stopPropagation() {
        this.propagationStopped = true;
    }

    public void recordVisistedDownStream(Object component){
        visitedComponentsDownstream.add(component);
    }

    public List<Object> getVisitedComponentsDownstream(){
        return visitedComponentsDownstream;
    }

    public Class<?> getEffectiveEventType(){
        return getEvent().getClass();
    }
}
