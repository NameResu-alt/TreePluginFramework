package org.treepluginframework.meta_events.event_propagation;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeReference;

import java.util.UUID;

public class TPFEventPropagationStartedMetaEvent extends TPFEventPropagationMetaEvent<TPFEventPropagationStartedMetaEvent>{
    private final DAGNodeReference startingNode;

    public TPFEventPropagationStartedMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version, UUID eventUUID, Class<?> eventClass, DAGNodeReference startingNode) {
        super(tpf_uuid, eventDescription, dagUUID, version,eventUUID, eventClass);
        this.startingNode = startingNode;
    }

    public DAGNodeReference getStartingNode(){
        return this.startingNode;
    }
}
