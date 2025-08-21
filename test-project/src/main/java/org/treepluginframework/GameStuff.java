package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.annotations.TPFNode;
import org.treepluginframework.annotations.TPFValue;
import org.treepluginframework.component_architecture.TPF;

import java.util.ArrayList;
import java.util.List;

@TPFNode
public class GameStuff {
    @TPFValue(fileName = "kitpvp.yml",location = "sold",defaultValue = "")
    public String status;

    @TPFValue(fileName = "kitpvp.yml", location = "mage")
    public int number = 16;

    List<Dup> dups = new ArrayList<>();
    TPF f;
    public GameStuff(TPF f){
        this.f = f;
        System.out.println("Is TPF Null?: " + (f == null));
    }

    ///
    /// Only danger here, is that if a new node is registered, it'll immediately
    /// Take in the current event. Right now, fine for behaviour. May not always be fine.
    ///
    @EventSubscription(priority =  100)
    public void tick(TickEvent event){
        System.out.println("Got the tick event: " + this.getClass().getCanonicalName() + " Gaming: " + status + " ?? " + number);
        Dup newDup = new Dup(dups.size());
        dups.add(newDup);
        f.getEventDispatcher().register(this, newDup, false);
    }

    private class Dup{

        @TPFValue(location = "nope", defaultValue = "12")
        private String val;

        private int count;

        public Dup(int count){
            this.count = count;
        }

        @EventSubscription
        public void innerTick(TickEvent event){
            System.out.println("Dup#"+count + " Checking in");
        }
    }
}
