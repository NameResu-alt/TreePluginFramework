package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.values.ClassValueMetadata;
import org.treepluginframework.values.FieldValueInfo;
import org.treepluginframework.values.TPFMetadataFile;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;

/***
 * Hold the values of the config file.
 */
public class TPFValueRepository {
    private static final String CONFIG_ENV_VAR = "TPF_CONFIG_PATH";
    private static final String DEFAULT_CONFIG_CLASSPATH = "application.properties";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    private HashMap<Class<?>,HashMap<String, Field>> cachedFields = new HashMap<>();
    private HashMap<String,File> configurationFiles = new HashMap<>();
    private HashMap<String,String> savedGlobalValues = new HashMap<>();

    //Filename, location, value
    private HashMap<String,HashMap<String,String>> savedConfigurationFileValues = new HashMap<>();
    private TPFMetadataFile metaFile;
    private File globalConfigFile;
    /***
     * I need to add the docker secrets part too.
     */
    public TPFValueRepository(TPFMetadataFile metadataFile){
        metaFile = metadataFile;
    }

    public void addGlobalConfigurationFile(File configurationFile){
        this.globalConfigFile = configurationFile;
    }

    public void addConfigurationFile(File configurationFile){
        configurationFiles.put(configurationFile.getName(),configurationFile);
    }

    public void loadAllValues(){
        //Can't load values if there's no meta-file anyways?
        if(metaFile == null) return;
        loadValuesFromDockerSecrets();
        loadGlobalConfigurationFileValues();
        loadAllConfigurationFileValues();
        loadEnvironmentValues();
        loadFieldCache();
    }

    private void loadValuesFromDockerSecrets() {
        for (String location : metaFile.globalValueLocations) {
            File secretFile = new File("/run/secrets/" + location);

            // Check if the file exists before trying to read
            if (secretFile.exists() && secretFile.isFile()) {
                try {
                    String value = Files.readString(secretFile.toPath()).trim(); // trim to remove trailing newlines
                    savedGlobalValues.put(location, value);
                } catch (IOException e) {
                    //System.err.println("Failed to read secret for key: " + location);
                    //e.printStackTrace();
                }
            }
        }
    }

    private void loadGlobalConfigurationFileValues(){
        File globalConfig = (globalConfigFile == null) ? findConfigurationFile() : globalConfigFile;
        if(globalConfig == null) return;
        globalConfigFile = globalConfig;

        HashMap<String,String> result = loadConfigFileValues(globalConfig, metaFile.globalValueLocations);
        for(String key : result.keySet()){
            if(savedGlobalValues.containsKey(key)){
                continue;
            }
            savedGlobalValues.put(key, result.get(key));
        }
    }

    private void loadAllConfigurationFileValues(){
        for(String fileName : configurationFiles.keySet()){
            File f = configurationFiles.get(fileName);
            if(!metaFile.fileValueLocations.containsKey(fileName)){
                continue;
            }
            loadConfigurationFile(f);
        }
    }

    private void loadEnvironmentValues(){
        for(String location : metaFile.globalValueLocations){
            if(savedGlobalValues.containsKey(location)) continue;
            String val = System.getenv(location);
            if(val != null)
            {
                savedGlobalValues.put(location,val);
            }
        }
    }

    private void loadFieldCache(){
        for(String className : metaFile.classes.keySet()){

            Class<?> wantedClass = null;
            try {
                wantedClass = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            ClassValueMetadata data = metaFile.classes.get(className);
            if(data.fields.isEmpty()) continue;

            HashMap<String, Field> fieldCache = cachedFields.computeIfAbsent(wantedClass,k-> new HashMap<>());

            //System.out.println("Class: " + className);
            for(String fieldName : data.fields.keySet()){
                Field neededField = null;
                try {
                    neededField = wantedClass.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
                neededField.setAccessible(true);
                fieldCache.put(fieldName,neededField);
            }
        }
    }

    public void loadConfigurationFile(File configurationFile){
        if(configurationFile == null){
            return;
        }
        String fileName = configurationFile.getName();
        HashMap<String,String> savedValues = loadConfigFileValues(configurationFile, metaFile.fileValueLocations.get(fileName));
        savedConfigurationFileValues.computeIfAbsent(fileName, k-> new HashMap<String,String>()).putAll(savedValues);
    }


    private File findConfigurationFile(){
        String configPath = System.getenv(CONFIG_ENV_VAR);
        System.out.println("Testing the EnvVar: " + CONFIG_ENV_VAR);

        if (configPath != null) {
            System.out.println("Environmental Variable (config path): " + configPath);

            // Validate the file extension
            String extension = null;
            if (configPath.contains(".")) {
                int dotIndex = configPath.lastIndexOf('.');
                extension = configPath.substring(dotIndex); // includes the dot, e.g., ".yml"
            }

            if (extension == null ||
                    !(extension.equals(".yml") || extension.equals(".yaml") || extension.equals(".properties"))) {
                throw new IllegalArgumentException("Unsupported or missing config file extension. Only .yml, .yaml, and .properties are supported. - " + configPath);
            }

            try (InputStream in = getClass().getClassLoader().getResourceAsStream(configPath)) {
                if (in == null) {
                    System.err.println("Could not find " + configPath + " in classpath resources.");
                    return null;
                }

                // Create temp file with correct extension
                File tempFile = File.createTempFile("tpf-config-", extension);
                tempFile.deleteOnExit();

                try (OutputStream out = new FileOutputStream(tempFile)) {
                    in.transferTo(out);
                }

                System.out.println("Loaded config from classpath, temp file: " + tempFile.getAbsolutePath());
                return tempFile;
                //loadFromFile(tempFile, CONFIG_ENV_VAR);

            } catch (IOException e) {
                System.err.println("Failed to load config file from classpath: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try (InputStream stream = cl.getResourceAsStream(DEFAULT_CONFIG_CLASSPATH)) {
                if (stream == null) {
                    System.err.println("No application.properties found on classpath.");
                    return null;
                }

                // Create temp file with correct extension
                File tempFile = File.createTempFile("tpf-config-", ".properties");
                tempFile.deleteOnExit();

                try (OutputStream out = new FileOutputStream(tempFile)) {
                    stream.transferTo(out);
                }

                System.out.println("Loaded config from classpath, temp file: " + tempFile.getAbsolutePath());
                return tempFile;
                //configProperties.load(stream);
                //System.out.println("Loaded config from classpath: " + path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load config from classpath: " + DEFAULT_CONFIG_CLASSPATH, e);
            }
        }

        return null;
    }


    private HashMap<String,String> loadConfigFileValues(File configFile, Set<String> keyLocations){
        if(configFile == null){
            return null;
        }

        String extension = getFileExtension(configFile).toLowerCase();
        if(extension.isEmpty()){
            System.out.println("The file " + configFile.getName() + " does not have an extension, can't read from it");
            return null;
        }

        HashMap<String,String> savedValues = new HashMap<>();

        switch(extension){
            case "properties":
                savedValues = readValuesFromProperties(configFile, keyLocations);
                break;
            case "yaml":
            case "yml":
                savedValues = readValuesFromYML(configFile, keyLocations);
                break;
            default:
                System.out.println("The file type of " + configFile.getName() + " is not supported");
                break;
        }

        return savedValues;
    }

    private HashMap<String,String> readValuesFromProperties(File propertiesFile, Set<String> wantedKeys){
        HashMap<String,String> savedValues = new HashMap<>();
        Properties configProperties = new Properties();
        try(FileInputStream fis = new FileInputStream(propertiesFile))
        {
            configProperties.load(fis);
            for(String loc : wantedKeys){
                if(configProperties.containsKey(loc)){
                    savedValues.put(loc,configProperties.getProperty(loc));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return savedValues;
    }

    private HashMap<String,String> readValuesFromYML(File yamlFile, Set<String> wantedKeys) {
        HashMap<String,String> savedValues = new HashMap<>();
        try (InputStream input = new FileInputStream(yamlFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);

            for (String location : wantedKeys) {
                String[] pathPieces = location.split("\\.");
                Object current = data;

                for (int i = 0; i < pathPieces.length; i++) {
                    if (!(current instanceof Map)) {
                        current = null;
                        break;
                    }
                    current = ((Map<?, ?>) current).get(pathPieces[i]);
                }

                if (current instanceof String value) {
                    savedValues.put(location,value);
                } else if (current != null) {
                    savedValues.put(location,current.toString());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return savedValues;
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot == -1 || lastDot == name.length() - 1) {
            return ""; // No extension or ends with dot
        }
        return name.substring(lastDot + 1); // Excludes the dot
    }


    public void printValues(){
        System.out.println("Saved Values:");
        for(String location : savedGlobalValues.keySet()){
            System.out.println("\t"+location+": "+ savedGlobalValues.get(location));
        }
    }

    public <T> T getGlobalValue(String location, Class<T> type){
        return (savedGlobalValues.containsKey(location)) ? convertStringToType(savedGlobalValues.get(location),type) : null;
    }

    public HashMap<String,Object> getFileValues(FileValueRequest request){

        if(request.fileName == null || !savedConfigurationFileValues.containsKey(request.fileName)) return null;

        HashMap<String,String> configValues = savedConfigurationFileValues.get(request.fileName);

        HashMap<String,Object> result = new HashMap<>();
        HashMap<String, Class<?>> wantedValues = request.wantedValues;

        HashSet<String> valuesToBeFound = new HashSet<>();
        for(String location : wantedValues.keySet())
        {
            if(!configValues.containsKey(location)){
                System.out.println("Have to search for the values");
                valuesToBeFound.add(location);
            }
        }

        //Any missing values are added to the config for later.
        if(!valuesToBeFound.isEmpty()){
            HashMap<String,String> missingValues = loadConfigFileValues(configurationFiles.get(request.fileName), valuesToBeFound);
            configValues.putAll(missingValues);
        }

        for(String location : wantedValues.keySet()){
            if(!configValues.containsKey(location)){
                result.put(location,null);
            }
            else
            {
                result.put(location, convertStringToType(configValues.get(location), wantedValues.get(location)));
            }
        }

        return result;
    }

    public void injectFields(Object object){
        if(object == null) return;

        String className = object.getClass().getCanonicalName();
        if(!cachedFields.containsKey(object.getClass())){
            System.out.println("Cached Fields does not have the class " + object.getClass());
            return;
        }

        if(!metaFile.classes.containsKey(className)){
            System.out.println("MetaFile Classes does not contain the class " + className);
            return;
        }

        System.out.println("Class made it through: " + className);

        HashMap<String,Field> fields = cachedFields.get(object.getClass());

        System.out.println("Cache: " + fields.keySet());

        //System.out.println("Class Name: " + className);
        Map<String, FieldValueInfo> wantedFields = metaFile.classes.get(className).fields;

        //File requests end up different, since a single file can have different types that are needed
        HashMap<String, FileValueRequest> fileRequests = new HashMap<>();
        //FileName, Location, corresponding field to set.
        //I need the field, and the default value.
        HashMap<String,HashMap<String,HashSet<FieldLocationStore>>> requestFields = new HashMap<>();

        //Location, and a HashSet of Field+Default Value

        for(String varName : fields.keySet()){
            Field f = fields.get(varName);
            FieldValueInfo annotationsInfo = wantedFields.get(varName);
            System.out.println(varName + " " + annotationsInfo);
            //If AnnotationsInfo has a fileName, that means that I'm expecting a configuration file.
            if(!annotationsInfo.fileName.isEmpty()){
                FileValueRequest request = fileRequests.computeIfAbsent(annotationsInfo.fileName, k -> new FileValueRequest(annotationsInfo.fileName));
                request.addWantedValue(annotationsInfo.location,f.getType());

                HashMap<String,HashSet<FieldLocationStore>> fieldMap = requestFields.computeIfAbsent(annotationsInfo.fileName, k -> new HashMap<>());
                HashSet<FieldLocationStore> neededFields = fieldMap.computeIfAbsent(annotationsInfo.location, k -> new HashSet<>());
                neededFields.add(new FieldLocationStore(f, annotationsInfo.defaultValue));
                continue;
            }
            Object neededValue = getGlobalValue(annotationsInfo.location,f.getType());

            if(neededValue == null){
                neededValue = convertStringToType(annotationsInfo.defaultValue, f.getType());
            }

            try {
                f.set(object,neededValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        for(String fileName : fileRequests.keySet()){
            FileValueRequest request = fileRequests.get(fileName);
            //The stored values is location, and Object
            //Values would be null if the file doesn't exist. That's why I check for values != null
            HashMap<String,Object> values = this.getFileValues(request);

            HashMap<String,HashSet<FieldLocationStore>> matchedFields = requestFields.get(fileName);

            for(String location : matchedFields.keySet()){
                for(FieldLocationStore store : matchedFields.get(location)){
                    //This means that I actually found the value, so I just set it.
                    if(values != null && values.containsKey(location) && values.get(location) != null){
                        try {
                            System.out.println("File Location: " + location +" Value: " + values.get(location));
                            store.field.set(object, values.get(location));
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    else if(!store.defaultValue.isEmpty())
                    {
                        Object neededValue = convertStringToType(store.defaultValue, store.field.getType());
                        try {
                            store.field.set(object,neededValue);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

        }


    }

    @SuppressWarnings("unchecked")
    public static <T> T convertStringToType(String value, Class<T> type) {
        if (value == null) return null;

        try {
            if (type == String.class) return (T) value;
            if (type == Integer.class || type == int.class) return (T) Integer.valueOf(value);
            if (type == Long.class || type == long.class) return (T) Long.valueOf(value);
            if (type == Double.class || type == double.class) return (T) Double.valueOf(value);
            if (type == Float.class || type == float.class) return (T) Float.valueOf(value);
            if (type == Boolean.class || type == boolean.class) return (T) Boolean.valueOf(value);
            if (type == Short.class || type == short.class) return (T) Short.valueOf(value);
            if (type == Byte.class || type == byte.class) return (T) Byte.valueOf(value);

            // Optional: Handle char and Character
            if (type == Character.class || type == char.class) {
                if (value.length() != 1) throw new IllegalArgumentException("Expected single character");
                return (T) Character.valueOf(value.charAt(0));
            }

            // Fall back to JSON deserialization for custom types
            return OBJECT_MAPPER.readValue(value, type);

        } catch (Exception e) {
            throw new RuntimeException("Failed to convert value '" + value + "' to type " + type.getSimpleName(), e);
        }
    }

    public static class FileValueRequest{
        String fileName;
        HashMap<String, Class<?>> wantedValues = new HashMap<>();

        public FileValueRequest(String fileName, HashMap<String,Class<?>> wantedValues){
            this.fileName = fileName;
            this.wantedValues = wantedValues;
        }

        public FileValueRequest(String fileName){
            this.fileName = fileName;
        }

        public void addWantedValue(String location, Class<?> type){
            this.wantedValues.put(location,type);
        }
    }

    private class FieldLocationStore{
        Field field;
        String defaultValue;

        public FieldLocationStore(Field field, String defaultValue){
            this.field = field;
            this.defaultValue = defaultValue;
        }
    }
}
