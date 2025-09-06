package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.hooks.TPFEventLog;
import org.treepluginframework.values.TPFEventFile;
import org.treepluginframework.values.TPFStructureFile;
import org.treepluginframework.values.TPFValueFile;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

//Need to make it an interface. So that I have a test version, and a real version.
public class TPF {
    private TPFNodeRepository nodeRepository;
    private TPFValueRepository valueRepository;
    private TPFEventDispatcher eventDispatcher;

    private TPFStructureFile structureFile;
    private TPFValueFile valueFile;
    private TPFEventFile eventFile;

    private static final Logger logger = Logger.getLogger(TPF.class.getName());

    private UUID tpfUUID = UUID.randomUUID();

    private HashMap<Class<?>,Object> loggers = new HashMap<>();


    public TPF(File globalConfigurationFile){
        setup(globalConfigurationFile);
    }

    public TPF(){
        setup(null);
    }

    public TPFNodeRepository getNodeRepository(){
        return this.nodeRepository;
    }

    public TPFValueRepository getValueRepository(){
        return this.valueRepository;
    }

    public TPFEventDispatcher getEventDispatcher(){
        return this.eventDispatcher;
    }

    /*
        In case multiple configuration files are needed for whatever reason.

     */

    public void addConfigurationFile(File file){
        this.valueRepository.addConfigurationFile(file);
    }

    private void setup(File configurationFile) {
        this.valueFile = findTPFValuesFile();
        this.structureFile = findTPFStructureFile();
        this.eventFile = findTPFEventFile();

        boolean hasMetadata = structureFile != null;
        boolean hasEvent = eventFile != null;

        if (!hasMetadata && !hasEvent) {
            // log: No metadata or event file found
            logger.warning("No metadata nor event file found");
            return;
        }

        if (hasMetadata) {
            setupMetadataRelatedComponents(configurationFile);
        }

        this.eventDispatcher = new TPFEventDispatcher(
                hasMetadata ? structureFile : null,
                hasEvent ? eventFile : null,
                hasMetadata ? nodeRepository : null
        );
    }

    private void setupMetadataRelatedComponents(File configurationFile) {
        this.valueRepository = new TPFValueRepository(this.valueFile);
        if(configurationFile != null){
            valueRepository.addGlobalConfigurationFile(configurationFile);
        }

        this.nodeRepository = new TPFNodeRepository(this, valueRepository, structureFile, this.valueFile);
    }


    public void start(){
        if(this.structureFile == null){
            logger.warning("There is no TPF META-INF file present, can't utilize TPF system.");
        }
        else
        {
            this.valueRepository.loadAllValues();
            nodeRepository.generateNodesAndResourcesV2();
        }

        eventDispatcher.setUpDAG();
        //nodeRepository.generateNodesAndResources();
    }

    public <T> T getNode(Class<T> classType){
        return this.nodeRepository.getNode(classType);
    }

    private TPFEventFile findTPFEventFile(){
        try(InputStream is = TPF.class.getClassLoader()
                .getResourceAsStream("META-INF/tpf/event.json")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                TPFEventFile metaFile = mapper.readValue(is, TPFEventFile.class);
                return metaFile;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private TPFValueFile findTPFValuesFile(){
        try(InputStream is = TPF.class.getClassLoader()
                .getResourceAsStream("META-INF/tpf/value.json")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                TPFValueFile metaFile = mapper.readValue(is, TPFValueFile.class);
                return metaFile;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private TPFStructureFile findTPFStructureFile(){
        try(InputStream is = TPF.class.getClassLoader()
                .getResourceAsStream("META-INF/tpf/structure.json")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                TPFStructureFile metaFile = mapper.readValue(is, TPFStructureFile.class);
                return metaFile;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public void injectFieldValues(Object ob){
        this.valueRepository.injectFields(ob);
    }

    private void tpfLogEvent(TPFEventLog log){

    }


}
