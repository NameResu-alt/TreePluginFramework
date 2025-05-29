package org.treepluginframework.EventSystem.EventArchitecture;

public class IEvent {

    private boolean propagationStopped = false;

    public String getDirection(){
        return "down";
    }


    public boolean isPropagationStopped() {
        return propagationStopped;
    }
}
