package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.values.TPFMetadataFile;

import java.io.*;
import java.util.logging.Logger;

//Need to make it an interface. So that I have a test version, and a real version.
public class TPF {
    private EventDispatcher eventDispatcher = new EventDispatcher();
    private TPFNodeRepository nodeRepository;
    private TPFValueRepository valueRepository;

    private final TPFMetadataFile metadataFile;

    private static final Logger logger = Logger.getLogger(TPF.class.getName());


    public TPF(File configurationFile){
        this.metadataFile = findTPFValuesFile();
        if(metadataFile == null) return;
        this.valueRepository = new TPFValueRepository(configurationFile, this.metadataFile);
        this.nodeRepository = new TPFNodeRepository(valueRepository, metadataFile);
    }

    public TPF(){
        this.metadataFile = findTPFValuesFile();
        if(metadataFile == null) return;
        this.valueRepository = new TPFValueRepository(this.metadataFile);
        this.nodeRepository = new TPFNodeRepository(valueRepository, metadataFile);
    }

    public void start(){
        if(this.metadataFile == null){
            logger.warning("There is no TPF META-INF file present, can't utilize TPF system.");
            return;
        }
        nodeRepository.generateNodesAndResourcesV2();
        //nodeRepository.generateNodesAndResources();
    }

    public <T> T getNode(Class<T> classType){
        return this.nodeRepository.getNode(classType);
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
}
