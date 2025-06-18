package org.treepluginframework.Testing.Events;


import org.treepluginframework.EventSubscription;
import org.treepluginframework.EventSystem.ComponentArchitecture.Component;
import org.treepluginframework.EventSystem.EventArchitecture.IEvent;

public class TestComponent extends Component {
    private int timesGottenTest;
    private String name;

    public TestComponent(String name){
        this.name = name;
    }

    @EventSubscription(priority = 10)
    public void handleTest(IEvent event){
        timesGottenTest += 1;
        System.out.println("Got the test with the adapter! " + timesGottenTest + " " + name);
    }
}
