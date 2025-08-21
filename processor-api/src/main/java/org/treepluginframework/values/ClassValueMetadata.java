package org.treepluginframework.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

//Parameters and fields is from whatever configuration file there is.
//However, parameters is assumed to be part of a constructor, so I think
//whatever constructor I choose, should be here as well.
//I should record what type the parameters have first,
//Then I should record what types I shoudl actually be providing to those parameters.

//This will make it easier to find the constructor, and then simple to just plug stuff in.
public class ClassValueMetadata {
    //Parameters and fields holds the TPFValues.
    //For fields, its simple since there can ever only be 1 field of that name in a class.
    //For parameters, it becomes trickier, since multiple constructors can have TPFValue.
    //Anything other than the main constructor is ignored, but I can end up with a situation where I overwrite the true constructor.
    //So to fix this, I need to make a signature based on what the Constructor's parameters.
    public Map<String,List<ParameterValueInfo>> parameters = new HashMap<>();
    public Map<String, FieldValueInfo> fields = new HashMap<>();



    @JsonIgnore
    public boolean isEmpty(){
        return parameters.isEmpty() && fields.isEmpty();
    }
}
