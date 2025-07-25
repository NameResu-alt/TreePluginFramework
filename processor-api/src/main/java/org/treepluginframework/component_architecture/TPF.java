package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.values.TPFMetadataFile;

import java.io.*;

//Need to make it an interface. So that I have a test version, and a real version.
public class TPF {
    private final EventDispatcher eventDispatcher = new EventDispatcher();
    private final TPFNodeRepository nodeRepository;
    private final TPFValueRepository valueRepository;

    private final TPFMetadataFile metadataFile;


    public TPF(File configurationFile){
        this.metadataFile = findTPFValuesFile();
        this.valueRepository = new TPFValueRepository(configurationFile, this.metadataFile);
        this.nodeRepository = new TPFNodeRepository(valueRepository, metadataFile);
    }

    public TPF(){
        this.metadataFile = findTPFValuesFile();
        this.valueRepository = new TPFValueRepository(this.metadataFile);
        this.nodeRepository = new TPFNodeRepository(valueRepository, metadataFile);
    }

    public void start(){
        nodeRepository.generateNodesAndResourcesV2();
        //nodeRepository.generateNodesAndResources();
    }

    public <T> T getNode(Class<T> classType){
        return this.nodeRepository.getNode(classType);
    }

    public void printSavedValues(){
        valueRepository.printValues();
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
