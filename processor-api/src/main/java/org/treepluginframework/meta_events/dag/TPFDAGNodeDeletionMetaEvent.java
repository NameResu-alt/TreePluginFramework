package org.treepluginframework.meta_events.dag;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeMetadata;

import java.util.UUID;

/*
    When a node is removed from the DAG
 */
public class TPFDAGNodeDeletionMetaEvent extends TPFDAGMetaEvent {
    //The DAG node that was deleted.
    private final DAGNodeMetadata removed;


    public TPFDAGNodeDeletionMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, int version, DAGNodeMetadata removed) {
        super(tpf_uuid, eventDescription, dagUUID, version);
        this.removed = removed;
    }
}
