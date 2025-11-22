package org.treepluginframework.meta_events.dag;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;

import java.util.UUID;

/*
    When an edge is added between two nodes in the DAG
 */
public class TPFDAGEdgeAdditionMetaEvent extends TPFDAGMetaEvent<TPFDAGEdgeAdditionMetaEvent>
{
    //These are both DAGNodeDetails, in the case where both nodes are new to the DAG, I keep track of their data.
    private final DAGNodeDetails from;
    private final DAGNodeDetails to;

    public TPFDAGEdgeAdditionMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version, DAGNodeDetails from, DAGNodeDetails to) {
        super(tpf_uuid, eventDescription, dagUUID, version);
        this.from = from;
        this.to = to;
    }
    //From and to

    public DAGNodeDetails getFrom() {
        return from;
    }

    public DAGNodeDetails getTo() {
        return to;
    }
}
