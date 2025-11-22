package org.treepluginframework.meta_events.structure;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeReference;

import java.util.List;
import java.util.UUID;

public class TPFStructureEdgeAddedEvent extends TPFStructureMetaEvent<TPFStructureEdgeAddedEvent>{
    //I'm going to have to repeat information from node added, since roots just do addNode on their own.
    //Honestly, I could just make it so that it's addEdge(null, Node) so that it goes throught he same place.
    //Actually, I only need information from the to. I don't need information from the from, due to topological sorting..
    private final DAGNodeReference parentReference;
    private final DAGNodeDetails newNode;
    private final List<String> expectedDependencies;
    private final List<String> injectedDependencies;

    public TPFStructureEdgeAddedEvent(UUID tpfUUID, String eventDescription, UUID dagUUID, long currentVersion, DAGNodeReference parentReference, DAGNodeDetails newNode, List<String> expectedDependencies, List<String> injectedDependencies) {
        super(tpfUUID, eventDescription, dagUUID, currentVersion);
        this.parentReference = parentReference;
        this.newNode = newNode;
        this.expectedDependencies = expectedDependencies;
        this.injectedDependencies = injectedDependencies;
    }


    public DAGNodeReference getParentReference() {
        return parentReference;
    }

    public DAGNodeDetails getNewNode() {
        return newNode;
    }

    public List<String> getExpectedDependencies() {
        return expectedDependencies;
    }

    public List<String> getInjectedDependencies() {
        return injectedDependencies;
    }

}
