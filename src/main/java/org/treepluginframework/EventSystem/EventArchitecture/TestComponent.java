package org.treepluginframework.EventSystem.EventArchitecture;


import org.treepluginframework.EventSystem.ComponentArchitecture.Component;
import org.treepluginframework.EventSystem.ComponentArchitecture.EventSubscription;
import org.treepluginframework.EventSystem.ComponentArchitecture.NativeEventAdapter;

public class TestComponent extends Component {
    private int timesGottenTest;
    private String name;

    public TestComponent(String name){
        this.name = name;
    }

    @EventSubscription(priority = 10)
    public void handleTest(TestEvent t, NativeEventAdapter adapter){

        timesGottenTest += 1;
        System.out.println("Got the test with the adapter! " + timesGottenTest + " " + name);
    }
}
