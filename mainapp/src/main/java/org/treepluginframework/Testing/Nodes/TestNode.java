package org.treepluginframework.Testing.Nodes;

import org.treepluginframework.EventSubscription;
import org.treepluginframework.TPFAutoWireChild;
import org.treepluginframework.TPFNode;
import org.treepluginframework.Testing.Events.TestEvent;

@TPFNode
public class TestNode {

    @TPFAutoWireChild
    private TestNode2 myChild;

    /*
    @TPFAutoWireChild
    private TestNode4 myOtherChild;
     */
    public boolean checkIfNull(){
        return myChild == null;
    }

    @EventSubscription
    public void checking(TestEvent event){
        System.out.println("Got the event and I should be happy");
    }
}
