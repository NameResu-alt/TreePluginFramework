package org.treepluginframework.Testing.Nodes;

import org.treepluginframework.EventSubscription;
import org.treepluginframework.TPFNode;
import org.treepluginframework.Testing.Events.TestEvent;

@TPFNode
public class TestNode4 {

    @EventSubscription(priority = 100)
    public void checking(TestEvent event){
        System.out.println("Hola");
    }
}
