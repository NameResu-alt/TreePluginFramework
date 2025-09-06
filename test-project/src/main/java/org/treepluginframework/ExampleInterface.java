package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;

public interface ExampleInterface {
    @EventSubscription
    default void jokingAround(TickEvent o){
        System.out.println("This is a default method");
    }

    /*
    @EventSubscription
    public void jokingAround(TickEvent o);
     */
}
