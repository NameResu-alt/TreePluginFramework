package org.treepluginframework.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.*;

@JsonSerialize()
public class TPFMetadataFile {
    public Map<String, ClassValueMetadata> classes = new HashMap<>();

    //The create order. Has to be this way, unfortunately.
    public LinkedHashMap<String, ConstructorInformation> constructorInformation = new LinkedHashMap<>();


    //Location of values that you need to store.
    public HashSet<String> locations = new HashSet<>();

    public Date timeCreated;

    public boolean error = false;

    public TPFMetadataFile(){

    }

    public TPFMetadataFile(HashMap<String, ClassValueMetadata> classes, LinkedHashMap<String,ConstructorInformation> constructorInformation, HashSet<String> locations){
        this.classes = classes;
        this.locations = locations;
        this.constructorInformation = constructorInformation;
        this.timeCreated = new Date();
    }


    public void printMetadataFile(){
        ObjectMapper mapper = new ObjectMapper();
        String json = null;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
            System.out.println(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @JsonIgnore
    public boolean isEmpty(){
        return classes.isEmpty();
    }

}

