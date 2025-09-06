package org.treepluginframework.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.*;

@JsonSerialize()
public class TPFStructureFile {
    //The create order. Has to be this way, unfortunately.

    public HashMap<String,String> aliases = new HashMap<>();

    public LinkedHashMap<String, ConstructorInformation> constructorInformation = new LinkedHashMap<>();
    public Date timeCreated;

    public boolean error = false;

    public TPFStructureFile(){

    }

    public TPFStructureFile(LinkedHashMap<String,ConstructorInformation> constructorInformation, HashMap<String,String> aliases){
        this.constructorInformation = constructorInformation;
        this.aliases = aliases;
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
        return constructorInformation.isEmpty();
    }

}

