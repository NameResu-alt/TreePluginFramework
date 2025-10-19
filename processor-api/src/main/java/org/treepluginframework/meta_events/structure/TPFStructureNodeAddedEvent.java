package org.treepluginframework.meta_events.structure;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeMetadata;

import java.util.UUID;

public class TPFStructureNodeAddedEvent extends TPFStructureMetaEvent{
    private final UUID dagUUID;
    private final DAGNodeMetadata nodeMetadata;

    public TPFStructureNodeAddedEvent(UUID tpfUUID, String eventDescription, UUID dagUUID, DAGNodeMetadata nodeMetadata) {
        super(tpfUUID, eventDescription);
        this.dagUUID = dagUUID;
        this.nodeMetadata = nodeMetadata;
    }
}
