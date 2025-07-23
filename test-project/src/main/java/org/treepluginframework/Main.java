package org.treepluginframework;

import org.treepluginframework.component_architecture.TPF;

public class Main {
    public static void main(String[] args) {
        TPF newTPF = new TPF();
        newTPF.start();


        TestClass c = newTPF.getNode(TestClass.class);
        System.out.println("Test class has value of: " + c.levelName);
        c.test();
        //newTPF.printSavedValues();
        //System.out.println("Hello, you here?!");
        //newTPF.start();
        //newTPF.testMetaINF();
    }
}