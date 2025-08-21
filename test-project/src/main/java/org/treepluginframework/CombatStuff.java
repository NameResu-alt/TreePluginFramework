package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.annotations.TPFNode;

@TPFNode
public class CombatStuff {
    @EventSubscription(priority = 1000)
    public void tick(TickEvent event){
        System.out.println("Got the tick event: " + this.getClass().getCanonicalName());
    }
}
