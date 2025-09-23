package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.values.*;
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

    //Class, OriginClass, FieldName, Field.
    private HashMap<Class<?>, DestinationClassFieldCache> cachedFields = new HashMap<>();

    //Cached Values.
    //FileName(N/A for global), location, type, value.
    private HashMap<String,HashMap<String,HashMap<Class<?>,Object>>> cachedValues = new HashMap<>();

    private HashMap<String,File> configurationFiles = new HashMap<>();
    private HashMap<String,String> savedGlobalValues = new HashMap<>();

    //Filename, location, value
    private HashMap<String,HashMap<String,String>> savedConfigurationFileValues = new HashMap<>();
    private TPFValueFile valueFile;
    private File globalConfigFile;
    /***
     * I need to add the docker secrets part too.
     */


    public TPFValueRepository(TPFValueFile valueFile){
        this.valueFile = valueFile;
    }

    public void addGlobalConfigurationFile(File configurationFile){
        this.globalConfigFile = configurationFile;
    }

    public void addConfigurationFile(File configurationFile){
        configurationFiles.put(configurationFile.getName(),configurationFile);
    }

    public void loadAllValues(){
        //Can't load values if there's no meta-file anyways?
        if(valueFile == null) return;
        loadValuesFromDockerSecrets();
        loadGlobalConfigurationFileValues();
        loadAllConfigurationFileValues();
        loadEnvironmentValues();
        loadFieldCache();
    }

    private void loadValuesFromDockerSecrets() {
        for (String location : valueFile.globalValueLocations) {
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

        HashMap<String,String> result = loadConfigFileValues(globalConfig, valueFile.globalValueLocations);
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
            if(!valueFile.fileValueLocations.containsKey(fileName)){
                continue;
            }
            loadConfigurationFile(f);
        }
    }

    private void loadEnvironmentValues(){
        for(String location : valueFile.globalValueLocations){
            if(savedGlobalValues.containsKey(location)) continue;
            String val = System.getenv(location);
            if(val != null)
            {
                savedGlobalValues.put(location,val);
            }
        }
    }

    private void loadFieldCache(){
        HashMap<String,Class<?>> alreadyFoundClasses = new HashMap<>();
        for(String className : valueFile.classData.keySet()){
            Class<?> wantedClass = alreadyFoundClasses.getOrDefault(className, null);
            if(wantedClass == null) {
                try {
                    wantedClass = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                alreadyFoundClasses.put(className,wantedClass);
            }

            ClassValueMetadata data = valueFile.classData.get(className);
            DestinationClassFieldCache newCache = cachedFields.computeIfAbsent(wantedClass,k->new DestinationClassFieldCache());

            for(String destinationClassName : data.fields.keySet()){
                Class<?> destinationClass = alreadyFoundClasses.getOrDefault(destinationClassName,null);

                if(destinationClass == null) {
                    try {
                        destinationClass = Class.forName(destinationClassName);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    alreadyFoundClasses.put(destinationClassName,destinationClass);
                }

                HashMap<String, VariableValueInfo> fields = data.fields.get(destinationClassName);
                for(String fieldName : fields.keySet()){
                    Field neededField = null;
                    try {
                        neededField = destinationClass.getDeclaredField(fieldName);
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                    neededField.setAccessible(true);
                    newCache.add(destinationClass, neededField,fields.get(fieldName));
                }
            }
        }
    }

    public void loadConfigurationFile(File configurationFile){
        if(configurationFile == null){
            return;
        }
        String fileName = configurationFile.getName();
        HashMap<String,String> savedValues = loadConfigFileValues(configurationFile, valueFile.fileValueLocations.get(fileName));
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
            case "json":
                savedValues = readValuesFromStructuredFile(configFile,keyLocations,extension);
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

    private HashMap<String, String> readValuesFromStructuredFile(File file, Set<String> wantedKeys, String extension) {
        HashMap<String, String> savedValues = new HashMap<>();

        try (InputStream input = new FileInputStream(file)) {
            Map<String, Object> data = switch (extension) {
                case "yaml", "yml" -> new Yaml().load(input);
                case "json" -> OBJECT_MAPPER.readValue(input, Map.class);
                default -> throw new IllegalArgumentException("Unsupported file type: " + file.getName());
            };

            // Determine file type and parse accordingly

            // Traverse nested keys
            for (String location : wantedKeys) {
                String[] pathPieces = location.split("\\.");
                Object current = data;

                for (String piece : pathPieces) {
                    if (!(current instanceof Map)) {
                        current = null;
                        break;
                    }
                    current = ((Map<?, ?>) current).get(piece);
                }

                if (current instanceof String strValue) {
                    savedValues.put(location, strValue);
                } else if (current != null) {
                    // Serialize other types to JSON string
                    savedValues.put(location, OBJECT_MAPPER.writeValueAsString(current));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return savedValues;
    }

    /*
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
                    savedValues.put(location,OBJECT_MAPPER.writeValueAsString(current));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return savedValues;
    }
     */

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

    public void getFileValues(FileValueRequest request){
        if(request.fileName == null || !savedConfigurationFileValues.containsKey(request.fileName)) return;

        HashMap<String,String> configValues = savedConfigurationFileValues.get(request.fileName);

        HashSet<String> valuesToBeFound = new HashSet<>();
        for(String location : request.wantedValues.keySet())
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
            System.out.println("ValuesTOBeFound: " + missingValues.toString());
        }

        for(String location : request.wantedValues.keySet()){
            List<WantedValue> want = request.wantedValues.get(location);

            for(WantedValue w : want){
                if(request.result.containsKey(location) && request.result.get(location).containsKey(w.type)){
                    //If you already found the value, no point in converting from string again.

                    System.out.println("I quit");
                    if(w.callback != null)
                        w.callback.valueReceived(request.result.get(location).get(w.type));
                    continue;
                }
                
                if(configValues.containsKey(location)){
                    System.out.println("I got inside!: " + w.type);
                    String val = configValues.get(location);
                    Object result = convertStringToType(val, w.type);
                    System.out.println("What I got: " + result);
                    request.result.computeIfAbsent(location,k-> new HashMap<>()).put(w.type,result);

                    System.out.println("Location: " + location + " Type: " + w.type + " Result: " + result);
                    if(w.callback != null)
                        w.callback.valueReceived(result);
                }
                else
                {
                    if(w.callback != null)
                        w.callback.failedToFindValue();
                }
            }
        }

    }

    /*
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
     */

    public void injectFields(Object object){
        if(object == null) return;

        if(!cachedFields.containsKey(object.getClass())){
            System.out.println("Cached Fields does not have the class " + object.getClass());
            return;
        }

        String className = object.getClass().getCanonicalName();
        System.out.println("Class made it through: " + className);

        DestinationClassFieldCache cache = cachedFields.get(object.getClass());
        List<Class<?>> origins = cache.getOrigins();


        HashMap<String, FileValueRequest> fileRequests = new HashMap<>();
        for(Class<?> origin : origins){
            HashSet<FieldAndLocation> fields = cache.getFieldsFromOrigin(origin);

            for(FieldAndLocation data : fields){
                Field field = data.field;
                VariableValueInfo info = data.info;
                if(!data.info.fileName.isBlank()){
                    //This is a file request.
                    FileValueRequest request = fileRequests.computeIfAbsent(data.info.fileName, FileValueRequest::new);
                    request.addWantedValue(data.info.location, field.getType(), new FileValueCallback() {
                        @Override
                        public void valueReceived(Object value) {
                            try {
                                field.set(object,value);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void failedToFindValue() {
                            Object neededValue = (!info.defaultValue.isBlank()) ? convertStringToType(info.defaultValue,field.getType()) : null;
                            if(neededValue != null){
                                try {
                                    field.set(object,neededValue);
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    });
                }
                else
                {
                    Object neededValue = getGlobalValue(info.location,field.getType());
                    if(neededValue == null){
                        neededValue = convertStringToType(info.defaultValue, field.getType());
                    }
                    try {
                        field.set(object,neededValue);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        for(String fileName : fileRequests.keySet()){
            this.getFileValues(fileRequests.get(fileName));
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

    public static class FileValueRequest {
        String fileName;

        //One location could have different types that are wanted. So that's why
        Map<String, List<WantedValue>> wantedValues = new HashMap<>();
        Map<String, Map<Class<?>, Object>> result = new HashMap<>();

        public FileValueRequest(String fileName){
            this.fileName = fileName;
        }

        public void addWantedValue(String location, Class<?> type, FileValueCallback callback){
            wantedValues.computeIfAbsent(location,k->new ArrayList<>()).add(new WantedValue(type,callback));
        }

        @SuppressWarnings("unchecked")
        public <T> T getValue(String location, Class<T> type){
            if(!result.containsKey(location)) return null;
            if(!result.get(location).containsKey(type)) return null;
            Object obj = result.get(location).get(type);

            if (type.isPrimitive()) {
                if (type == int.class) return (T) (Integer) obj;
                if (type == long.class) return (T) (Long) obj;
                if (type == double.class) return (T) (Double) obj;
                if (type == float.class) return (T) (Float) obj;
                if (type == boolean.class) return (T) (Boolean) obj;
                if (type == short.class) return (T) (Short) obj;
                if (type == byte.class) return (T) (Byte) obj;
                if (type == char.class) return (T) (Character) obj;
            }

            return type.cast(obj);
        }
    }

    public static class WantedValue {
        Class<?> type;
        FileValueCallback callback;

        public WantedValue(Class<?> type, FileValueCallback callback) {
            this.type = type;
            this.callback = callback;
        }
    }

    public static interface FileValueCallback{
        public void valueReceived(Object value);
        public void failedToFindValue();
    }

    private class FieldLocationStore{
        Field field;
        String defaultValue;

        public FieldLocationStore(Field field, String defaultValue){
            this.field = field;
            this.defaultValue = defaultValue;
        }
    }

    private static class DestinationClassFieldCache {
        private final Map<Class<?>, HashSet<FieldAndLocation>> fields = new HashMap<>();

        void add(Class<?> origin, Field field, VariableValueInfo info) {
            fields
                    .computeIfAbsent(origin, k -> new HashSet<>()).add(new FieldAndLocation(field,info));
        }

        public HashSet<FieldAndLocation> getFieldsFromOrigin(Class<?> origin) {
            return fields.getOrDefault(origin, new HashSet<>());
        }

        public List<Class<?>> getOrigins(){
            return new ArrayList<>(fields.keySet());
        }
    }

    private static class FieldAndLocation{
        public Field field;
        public VariableValueInfo info;

        public FieldAndLocation(Field field, VariableValueInfo info){
            this.field = field;
            this.info = info;
        }
    }
}
