package org.treepluginframework.values;

import java.util.ArrayList;
import java.util.List;

public class ConstructorInformation {
    //The parameters of the constructor that will be used.
    public List<String> neededConstructorParameters = new ArrayList<>();

    //The actual types that I'll need. For example, if selected has an abstract class for a value, but needed contains a definition of it.
    public List<String> desiredConstructorParameters = new ArrayList<>();

    public ConstructorInformation(){

    }

}
