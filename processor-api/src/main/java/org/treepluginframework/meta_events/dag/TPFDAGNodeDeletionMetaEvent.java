package org.treepluginframework.meta_events.dag;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeReference;

import java.util.UUID;

/*
    When a node is removed from the DAG
 */
public class TPFDAGNodeDeletionMetaEvent extends TPFDAGMetaEvent<TPFDAGNodeDeletionMetaEvent> {
    //The DAG node that was deleted.
    private final DAGNodeReference removed;


    public TPFDAGNodeDeletionMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version, DAGNodeReference removed) {
        super(tpf_uuid, eventDescription, dagUUID, version);
        this.removed = removed;
    }

    public DAGNodeReference getRemoved() {
        return removed;
    }
}
