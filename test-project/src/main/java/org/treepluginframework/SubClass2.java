package org.treepluginframework;

import org.treepluginframework.annotations.TPFNode;
import org.treepluginframework.annotations.TPFPrimary;

@TPFNode(alias = "fake")
public class SubClass2 implements InterfaceTesting{
    @Override
    public void sayStuff() {
        System.out.println("SubClass2 Represent!!!");
    }
}
