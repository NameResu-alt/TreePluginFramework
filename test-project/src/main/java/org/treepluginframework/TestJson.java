package org.treepluginframework;

public class TestJson {
    public String name;
    public int age;
    public boolean alive;

    public TestJson(){

    }

    public TestJson(String name, int age, boolean alive){
        this.name = name;
        this.age = age;
        this.alive = alive;
    }

    @Override
    public String toString(){
        return name + " " + age + " " + alive;
    }
}
