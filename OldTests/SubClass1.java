package org.treepluginframework;

import org.treepluginframework.annotations.TPFNode;
import org.treepluginframework.annotations.TPFPrimary;
import org.treepluginframework.annotations.TPFValue;

@TPFNode(alias = "real")
@TPFPrimary
public class SubClass1 extends AbstractTPFClass implements InterfaceTesting{

    @TPFValue(location = "testing2",defaultValue = "N/A")
    private String api_key;

    public SubClass1(@TPFValue(location = "otherplace",defaultValue = "5") Integer test){
        System.out.println("Hello, I exist!: " + test);
    }

    @Override
    public void sayStuff() {
        System.out.println("Im SubClass1, what about it?");
    }
}
