package org.treepluginframework.EventSystem.EventArchitecture;

import org.treepluginframework.EventSubscription;
import org.treepluginframework.EventSystem.ComponentArchitecture.Component;
public class Test2Component extends Component {

    @EventSubscription
    public void check(Test2Event event){
        System.out.println("Got the second event!");
    }

    @EventSubscription(priority = 100)
    public void other(TestEvent event){
        System.out.println("I'm different!");
    }

}
