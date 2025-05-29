package org.treepluginframework.EventSystem.ComponentArchitecture;

import org.treepluginframework.EventSystem.EventArchitecture.IEvent;

public class NativeEventAdapter extends EventAdapter<IEvent>{
    public NativeEventAdapter(IEvent event) {
        super(event);
    }
}
