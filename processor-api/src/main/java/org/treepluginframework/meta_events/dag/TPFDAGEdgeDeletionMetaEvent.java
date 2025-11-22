package org.treepluginframework.meta_events.dag;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeReference;

import java.util.UUID;

/*
    When an edge is removed between two nodes in the DAG
 */
public class TPFDAGEdgeDeletionMetaEvent extends TPFDAGMetaEvent<TPFDAGEdgeDeletionMetaEvent> {
    //References, since removing an edge doesn't mean that the node still isn't in the DAG
    private final DAGNodeReference from;
    private final DAGNodeReference to;

    public TPFDAGEdgeDeletionMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version, DAGNodeReference from, DAGNodeReference to) {
        super(tpf_uuid, eventDescription, dagUUID, version);
        this.from = from;
        this.to = to;
    }

    public DAGNodeReference getTo() {
        return to;
    }

    public DAGNodeReference getFrom() {
        return from;
    }
}
