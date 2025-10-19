package org.treepluginframework.meta_events.dag;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeMetadata;

import java.util.UUID;

/*
    When an edge is added between two nodes in the DAG
 */
public class TPFDAGEdgeAdditionMetaEvent extends TPFDAGMetaEvent
{
    private final DAGNodeMetadata from;
    private final DAGNodeMetadata to;

    public TPFDAGEdgeAdditionMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, int version, DAGNodeMetadata from, DAGNodeMetadata to) {
        super(tpf_uuid, eventDescription, dagUUID, version);
        this.from = from;
        this.to = to;
    }
    //From and to
}
