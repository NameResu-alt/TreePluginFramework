package org.treepluginframework;

import org.treepluginframework.EventSystem.ComponentArchitecture.Component;
import org.treepluginframework.EventSystem.ComponentArchitecture.EventDispatcher;
import org.treepluginframework.EventSystem.EventArchitecture.Test2Component;
import org.treepluginframework.EventSystem.EventArchitecture.TestComponent;
import org.treepluginframework.EventSystem.EventArchitecture.TestEvent;

public class Main {
    public static void main(String[] args) {

        TestComponent parent = Component.register(()->new TestComponent("Apple"), null);
        TestComponent c = Component.register(()->new TestComponent("Lemon"),parent);
        TestComponent orange = Component.register(()->new TestComponent("Orange"),parent);

        TestComponent solo = Component.register(()->new TestComponent("Solo"),parent);

        Test2Component deep_test = Component.register(()-> new Test2Component(),parent);

        EventDispatcher.getInstance().emit(parent,new TestEvent());
        //EventDispatcher.getInstance().emit(c,new TestEvent());

        //httpTest();
    }
}