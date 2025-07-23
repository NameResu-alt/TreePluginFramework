package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.values.TPFMetadataFile;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

/***
 * Hold the values of the config file.
 */
public class TPFValueRepository {
    private static final String CONFIG_ENV_VAR = "TPF_CONFIG_PATH";
    private static final String DEFAULT_CONFIG_CLASSPATH = "application.properties";
    private HashMap<String,String> savedValues = new HashMap<>();
    private TPFMetadataFile metaFile;
    /***
     * I need to add the docker secrets part too.
     */

    public TPFValueRepository(File configFile, TPFMetadataFile metadataFile){
        metaFile = metadataFile;
        if(metaFile == null)
            return;

        loadValuesFromDockerSecrets();
        loadConfigFileValues(configFile);
        loadEnvironmentValues();
    }

    //Need a way to handle .yml/.yaml and .properties.
    //I want to allow for both, so yeah...
    public TPFValueRepository(TPFMetadataFile metadataFile) {
        metaFile = metadataFile;
        if(metaFile == null)
            return;

        File configFile = findConfigurationFile();
        loadValuesFromDockerSecrets();
        loadConfigFileValues(configFile);
        loadEnvironmentValues();
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
                throw new IllegalArgumentException("Unsupported or missing config file extension. Only .yml, .yaml, and .properties are supported.");
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

    private void loadEnvironmentValues(){
        for(String location : metaFile.locations){
            if(savedValues.containsKey(location)) continue;
            String val = System.getenv(location);
            if(val != null)
            {
                savedValues.put(location,val);
            }
        }
    }

    private void loadConfigFileValues(File configFile){
        if(configFile == null){
            return;
        }

        String extension = getFileExtension(configFile).toLowerCase();
        if(extension.isEmpty()){
            System.out.println("The file " + configFile.getName() + " does not have an extension, can't read from it");
            return;
        }


        switch(extension){
            case "properties":
                readValuesFromProperties(configFile);
                break;
            case "yaml":
            case "yml":
                readValuesFromYML(configFile);
                break;
            default:
                System.out.println("The file type of " + configFile.getName() + " is not supported");
                break;
        }
    }

    private void loadValuesFromDockerSecrets() {
        for (String location : metaFile.locations) {
            File secretFile = new File("/run/secrets/" + location);

            // Check if the file exists before trying to read
            if (secretFile.exists() && secretFile.isFile()) {
                try {
                    String value = Files.readString(secretFile.toPath()).trim(); // trim to remove trailing newlines
                    savedValues.put(location, value);
                } catch (IOException e) {
                    //System.err.println("Failed to read secret for key: " + location);
                    //e.printStackTrace();
                }
            }
        }
    }

    private void readValuesFromProperties(File propertiesFile){
        Properties configProperties = new Properties();
        try(FileInputStream fis = new FileInputStream(propertiesFile))
        {
            configProperties.load(fis);

            HashSet<String> locs = metaFile.locations;
            for(String loc : locs){
                if(configProperties.containsKey(loc)){
                    savedValues.put(loc, configProperties.getProperty(loc));
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readValuesFromYML(File yamlFile) {
        try (InputStream input = new FileInputStream(yamlFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);

            for (String location : metaFile.locations) {
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
                    savedValues.put(location, value);
                } else if (current != null) {
                    savedValues.put(location, current.toString()); // fallback for numbers, booleans, etc.
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
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
        for(String location : savedValues.keySet()){
            System.out.println("\t"+location+": "+savedValues.get(location));
        }
    }

    public <T> T getValue(String location, Class<T> type){
        return (savedValues.containsKey(location)) ? convertStringToType(savedValues.get(location),type) : null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T convertStringToType(String value, Class<T> type) {
        if (value == null) return null;

        try {
            if (type == String.class) return (T) value;
            if (type == Integer.class) return (T) Integer.valueOf(value);
            if (type == Long.class) return (T) Long.valueOf(value);
            if (type == Double.class) return (T) Double.valueOf(value);
            if (type == Float.class) return (T) Float.valueOf(value);
            if (type == Boolean.class) return (T) Boolean.valueOf(value);
            if (type == Short.class) return (T) Short.valueOf(value);
            if (type == Byte.class) return (T) Byte.valueOf(value);
            // Add custom converters as needed

            throw new IllegalArgumentException("Unsupported type: " + type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert value '" + value + "' to type " + type.getSimpleName(), e);
        }
    }

}
