package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.annotations.TPFNode;

@TPFNode
public class EventEntryPoint {
    private CombatStuff combatStuff;
    private GameStuff gameStuff;

    public EventEntryPoint(CombatStuff combatStuff, GameStuff gameStuff){
        this.combatStuff = combatStuff;
        this.gameStuff = gameStuff;
    }

    @EventSubscription
    public void tick(TickEvent event){
        System.out.println("Got the tick event: " + this.getClass().getCanonicalName());
    }
}
