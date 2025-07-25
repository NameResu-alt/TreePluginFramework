package org.treepluginframework;

import org.treepluginframework.annotations.TPFNode;
import org.treepluginframework.annotations.TPFValue;

@TPFNode(alias = "Lmao")
public class TestClass2 {
    @TPFValue(location = "level.description",defaultValue = "missing level description")
    public String levelName;

    public TestClass2(){

    }
}
