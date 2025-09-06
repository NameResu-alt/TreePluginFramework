package org.treepluginframework.values;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

public class TPFValueFile {
    //Class, fields.
    public HashMap<String,ClassValueMetadataV2> classData = new HashMap<>();
    public HashSet<String> globalValueLocations = new HashSet<>();
    public HashMap<String,HashSet<String>> fileValueLocations = new HashMap<>();
    public Date timeCreated;

    public TPFValueFile(){

    }

    public TPFValueFile(HashMap<String, ClassValueMetadataV2> classData, HashSet<String> globalValueLocations, HashMap<String, HashSet<String>> fileValueLocations) {
        this.classData = classData;
        this.globalValueLocations = globalValueLocations;
        this.fileValueLocations = fileValueLocations;
        this.timeCreated = new Date();
    }

    public void merge(TPFValueFile otherFile){
        this.classData.putAll(otherFile.classData);
        this.globalValueLocations.addAll(otherFile.globalValueLocations);
        this.fileValueLocations.putAll(otherFile.fileValueLocations);
    }
}
