package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;

public class Class1 implements ExampleInterface{
    @Override
    @EventSubscription
    public void jokingAround(TickEvent o){
        System.out.println("This is a default method, but not an interface Class1");
    }
}
