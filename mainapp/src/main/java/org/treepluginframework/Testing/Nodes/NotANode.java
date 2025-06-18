package org.treepluginframework.Testing.Nodes;

import org.treepluginframework.EventSubscription;
import org.treepluginframework.TPFAutoWireChild;
import org.treepluginframework.Testing.Events.TestEvent;

public class NotANode {

    @EventSubscription(priority = 1000)
    public void checking(TestEvent event){
        System.out.println("I'm not a node");
    }
}
