package org.treepluginframework;

import org.treepluginframework.annotations.TPFConstructor;
import org.treepluginframework.annotations.TPFNode;
import org.treepluginframework.annotations.TPFQualifier;
import org.treepluginframework.annotations.TPFValue;

@TPFNode(alias = "Testing")
public class TestClass {

    @TPFValue(location = "level.name")
    public String levelName;

    @TPFValue(location = "level.difficulty")
    public Integer levelDifficulty;

    private TestClass2 testClass2;

    private InterfaceTesting storedVal;

    //
     //InterfaceTesting test
    @TPFConstructor
    public TestClass(TestClass2 se, TestClass3 test2, @TPFQualifier(className = "org.treepluginframework.SubClass2") InterfaceTesting t , @TPFValue(location="first_location",defaultValue = "31") int check){
        this.testClass2 = se;
        this.storedVal = t;
        System.out.println("Im inside testclass and my check value is: " + check);
    }


    public void test(){
        if(testClass2 != null){
            System.out.println("Name of the inner class level: " + testClass2.levelName);
        }

        if(storedVal != null){
            storedVal.sayStuff();
        }
    }
}
