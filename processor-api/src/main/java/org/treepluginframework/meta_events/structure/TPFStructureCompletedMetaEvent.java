package org.treepluginframework.meta_events.structure;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;

import java.util.List;
import java.util.UUID;

/***
 * Final Meta Event called once the initial creation of the DAG tree is made.
 * Contains all of the information of the DAG structure nodes.
 */
public class TPFStructureCompletedMetaEvent extends TPFStructureMetaEvent<TPFStructureCompletedMetaEvent> {
    private final List<DAGNodeDetails> nodesCreated;

    public TPFStructureCompletedMetaEvent(UUID tpfUUID, String eventDescription, UUID dagUUID, long finalVersion, List<DAGNodeDetails> nodesCreated) {
        super(tpfUUID, eventDescription, dagUUID, finalVersion);
        this.nodesCreated = nodesCreated;
    }

    public List<DAGNodeDetails> getNodesCreated() {
        return nodesCreated;
    }
}
