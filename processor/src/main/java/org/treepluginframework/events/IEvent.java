package org.treepluginframework.events;

public class IEvent {

    private boolean propagationStopped = false;

    public String getDirection(){
        return "down";
    }


    public boolean isPropagationStopped() {
        return propagationStopped;
    }
}
