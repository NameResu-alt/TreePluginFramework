package org.treepluginframework.Testing.Nodes;

import org.treepluginframework.EventSubscription;
import org.treepluginframework.TPFAutoWireChild;
import org.treepluginframework.TPFNode;
import org.treepluginframework.Testing.Events.TestEvent;

@TPFNode
public class TestNode3 {

    @EventSubscription(priority = 100)
    public void checking(TestEvent event){
        System.out.println("Shouldn't be running");
    }
}
