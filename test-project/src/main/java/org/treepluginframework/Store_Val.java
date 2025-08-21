package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.annotations.TPFValue;
import org.treepluginframework.events.IEvent;
import org.treepluginframework.events.NativeEventAdapter;
import org.treepluginframework.events.ScuffedAdapter;

public class Store_Val {
    @TPFValue(location = "testing", defaultValue = "Nothing here")
    public String val;

    @EventSubscription
    public void verify(IEvent basic, NativeEventAdapter f){

    }
}
