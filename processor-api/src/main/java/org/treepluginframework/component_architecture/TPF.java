package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.meta_events.TPFMetaEvent;
import org.treepluginframework.values.TPFEventFile;
import org.treepluginframework.values.TPFStructureFile;
import org.treepluginframework.values.TPFValueFile;

import java.io.*;
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

    private final UUID tpfUUID = UUID.randomUUID();

    public TPF(File globalConfigurationFile){
        setup(globalConfigurationFile);
    }

    public TPF(){
        setup(null);
    }

    public UUID getTpfUUID(){return tpfUUID;};

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
        this.valueFile = loadMetaFile("META-INF/tpf/value.json", TPFValueFile.class);;
        this.structureFile = loadMetaFile("META-INF/tpf/structure.json", TPFStructureFile.class);
        this.eventFile = loadMetaFile("META-INF/tpf/event.json", TPFEventFile.class);

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
                hasMetadata ? nodeRepository : null,
                this
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


    private <T> T loadMetaFile(String path, Class<T> type) {
        try (InputStream is = TPF.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return new ObjectMapper().readValue(is, type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }


    public void injectFieldValues(Object ob){
        this.valueRepository.injectFields(ob);
    }

    void logMetaEvent(TPFMetaEvent<?> metaEvent){
        this.eventDispatcher.emitMetaEvent(metaEvent);
    }


    public void addMetaEventListener(Object metaEventListener){
        if(metaEventListener == null) return;
        this.getEventDispatcher().addMetaEventListener(metaEventListener);
    }

}
