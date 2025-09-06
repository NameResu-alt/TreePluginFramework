package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.annotations.TPFValue;
import org.treepluginframework.component_architecture.TPF;

public class ExtendedGameStuff extends GameStuff{
    @TPFValue(fileName = "kitpvp.yml", location = "mage")
    public int number = 16;



    public ExtendedGameStuff(TPF f, TestJson g) {
        super(f,g);
    }

    //@Override
    @EventSubscription(priority = 100)
    public void clonedEvent(TickEvent event){
        System.out.println("I'm here too, but I'm a child!");
    }
}
