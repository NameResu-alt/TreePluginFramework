package org.treepluginframework.meta_events.dag;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;

import java.util.UUID;

public class TPFDAGNodeAdditionMetaEvent extends TPFDAGMetaEvent<TPFDAGNodeAdditionMetaEvent>{
    DAGNodeDetails newNode;
    public TPFDAGNodeAdditionMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version, DAGNodeDetails newNode) {
        super(tpf_uuid, eventDescription, dagUUID, version);
        this.newNode = newNode;
    }

    public DAGNodeDetails getNewNode(){
        return newNode;
    }
}
