package org.treepluginframework.component_architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.treepluginframework.values.TPFMetadataFile;

import java.io.*;
import java.util.List;

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
        nodeRepository.generateNodesAndResources();
    }

    public <T> T getNode(Class<T> classType){
        return this.nodeRepository.getNode(classType);
    }

    public void printSavedValues(){
        valueRepository.printValues();
    }


    public void testMetaINF()
    {
        try(InputStream is = TPF.class.getClassLoader()
                .getResourceAsStream("META-INF/tpf/values.json")) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                List<String> lines = reader.lines().toList();
                System.out.println(lines);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TPFMetadataFile findTPFValuesFile(){
        try(InputStream is = TPF.class.getClassLoader()
                .getResourceAsStream("META-INF/tpf/values.json")) {
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

}
