package org.treepluginframework.meta_events.structure;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeReference;

import java.util.List;
import java.util.UUID;

public class TPFStructureNodeAddedEvent extends TPFStructureMetaEvent<TPFStructureNodeAddedEvent>{
    //Can't be node details, since it wouldn't have its children or other info yet.
    private final DAGNodeDetails nodeCreated;
    private final List<String> expectedDependencies;
    private final List<String> injectedDependencies;

    public TPFStructureNodeAddedEvent(UUID tpfUUID, String eventDescription, UUID dagUUID, long currentVersion, DAGNodeDetails nodeDetails, List<String> expectedDependencies, List<String> injectedDependencies) {
        super(tpfUUID, eventDescription, dagUUID, currentVersion);

        this.nodeCreated = nodeDetails;
        this.expectedDependencies = expectedDependencies;
        this.injectedDependencies = injectedDependencies;
    }

    public DAGNodeDetails getNodeCreated() {
        return nodeCreated;
    }

    public List<String> getExpectedDependencies() {
        return expectedDependencies;
    }

    public List<String> getInjectedDependencies() {
        return injectedDependencies;
    }

}
