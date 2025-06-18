package org.treepluginframework.Testing.Nodes;

import org.treepluginframework.EventSubscription;
import org.treepluginframework.TPFAutoWireChild;
import org.treepluginframework.TPFNode;
import org.treepluginframework.Testing.Events.TestEvent;

@TPFNode
public class TestNode2 {

    @TPFAutoWireChild
    private TestNode3 myOtherChild;

    @EventSubscription
    public void checking(TestEvent event){
        System.out.println("I'm here!");
    }
}
