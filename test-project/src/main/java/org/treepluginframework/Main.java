package org.treepluginframework;

import org.treepluginframework.component_architecture.TPF;

public class Main {
    public static void main(String[] args)
    {
        TPF check = new TPF();
        check.start();

        Store_Val v = new Store_Val();
        check.injectValues(v);
        System.out.println("Value of v: " + v.val);
    }
}
