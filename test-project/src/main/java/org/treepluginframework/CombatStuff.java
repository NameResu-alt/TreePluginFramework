package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.annotations.TPFNode;
import org.treepluginframework.annotations.TPFValue;

@TPFNode
public class CombatStuff {

    @TPFValue(fileName = "kitpvp.yml",location = "person")
    TestJson check;

    @EventSubscription(priority = 1000)
    public void tick(TickEvent event){
        System.out.println("Got the tick event: " + this.getClass().getCanonicalName());
        System.out.println("My Json: " + check);
    }
}
