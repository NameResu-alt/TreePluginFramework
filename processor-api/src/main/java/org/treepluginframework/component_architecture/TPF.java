package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.hooks.TPFEventLog;
import org.treepluginframework.values.TPFEventFile;
import org.treepluginframework.values.TPFMetadataFile;

import java.io.*;
import java.util.UUID;
import java.util.logging.Logger;

//Need to make it an interface. So that I have a test version, and a real version.
public class TPF {
    private TPFNodeRepository nodeRepository;
    private TPFValueRepository valueRepository;
    private TPFEventDispatcher eventDispatcher;

    private TPFMetadataFile metadataFile;
    private TPFEventFile eventFile;

    private static final Logger logger = Logger.getLogger(TPF.class.getName());

    private UUID tpfUUID = UUID.randomUUID();


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

    }




    private void setup(File configurationFile) {
        this.metadataFile = findTPFValuesFile();
        this.eventFile = findTPFEventFile();

        boolean hasMetadata = metadataFile != null;
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
                hasMetadata ? metadataFile : null,
                hasEvent ? eventFile : null,
                hasMetadata ? nodeRepository : null
        );
    }

    private void setupMetadataRelatedComponents(File configurationFile) {
        this.valueRepository = new TPFValueRepository(this.metadataFile);
        if(configurationFile != null){
            valueRepository.addGlobalConfigurationFile(configurationFile);
        }

        this.nodeRepository = new TPFNodeRepository(this, valueRepository, metadataFile);
    }


    public void start(){
        if(this.metadataFile == null){
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

    private TPFMetadataFile findTPFValuesFile(){
        try(InputStream is = TPF.class.getClassLoader()
                .getResourceAsStream("META-INF/tpf/metadata.json")) {
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                TPFMetadataFile metaFile = mapper.readValue(is, TPFMetadataFile.class);
                return metaFile;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public void injectValues(Object ob){
        this.valueRepository.injectFields(ob);
    }

    private void tpfLogEvent(TPFEventLog log){

    }


}
