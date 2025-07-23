package org.treepluginframework;

import org.treepluginframework.annotations.*;

@TPFNode(alias = "Testing")
public class TestClass {

    @TPFValue(location = "level.name")
    public String levelName;

    @TPFValue(location = "level.difficulty")
    public Integer levelDifficulty;

    private TestClass2 testClass2;

    public TestClass(){

    }

    @TPFConstructor
    public TestClass(TestClass2 se, TestClass3 test2, @TPFQualifier(specifiedClass = SubClass1.class) InterfaceTesting test,@TPFValue(location="first_location",defaultValue = "31") Integer check){
        this.testClass2 = se;
        System.out.println("Im inside testclass and my check value is: " + check);
    }

    public void test(){
        if(testClass2 != null){
            System.out.println("Name of the inner class level: " + testClass2.levelName);
        }
    }
}
