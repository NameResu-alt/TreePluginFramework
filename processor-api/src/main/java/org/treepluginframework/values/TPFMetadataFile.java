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


    /***
     * Location of values from Docker Secrets -> Default Configuration File -> Environment Variables
     ***/
    public HashSet<String> globalValueLocations = new HashSet<>();

    /***
     * Location of values from specific configuration files that are loaded during the start of runtime.
     ***/
    public HashMap<String,HashSet<String>> fileValueLocations = new HashMap<>();

    public Date timeCreated;

    public boolean error = false;

    public TPFMetadataFile(){

    }

    public TPFMetadataFile(HashMap<String, ClassValueMetadata> classes, LinkedHashMap<String,ConstructorInformation> constructorInformation, HashSet<String> globalLocations, HashMap<String,HashSet<String>> configFileLocations){
        this.classes = classes;
        this.globalValueLocations = globalLocations;
        this.constructorInformation = constructorInformation;
        this.fileValueLocations = configFileLocations;
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

