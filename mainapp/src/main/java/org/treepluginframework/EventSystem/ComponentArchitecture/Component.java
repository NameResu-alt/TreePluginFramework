package org.treepluginframework.EventSystem.ComponentArchitecture;

import java.util.function.Supplier;

public class Component {
    private Component parent;

    public static void delete(Component del){
        //Calls the method in EventDistributer to unbind.
        EventDispatcher.getInstance().unregisterComponent(del);
        //Then calls any special methods the object has to be removed.
        del.deleteProcedures();
    }

    public static <T extends Component> T register(Supplier<T> supplier, Component parent){
        T instance = supplier.get();
        //EventDistributer.add myself to subscriptions.
        System.out.println("Registering a component: " + instance.getClass());
        EventDispatcher.getInstance().registerComponent(instance,parent);
        return instance;
    }

    public void deleteProcedures(){

    }
}
