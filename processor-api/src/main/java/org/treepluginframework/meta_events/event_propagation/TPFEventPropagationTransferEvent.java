package org.treepluginframework.meta_events.event_propagation;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeDetails;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeReference;

import java.util.UUID;

public class TPFEventPropagationTransferEvent extends TPFEventPropagationMetaEvent<TPFEventPropagationTransferEvent>{
    private final DAGNodeReference from;
    private final DAGNodeReference to;

    public TPFEventPropagationTransferEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version, UUID eventUUID, Class<?> eventClass, DAGNodeReference from, DAGNodeReference to) {
        super(tpf_uuid, eventDescription, dagUUID, version,eventUUID, eventClass);

        this.from = from;
        this.to = to;
    }

    public DAGNodeReference getFrom() {
        return from;
    }

    public DAGNodeReference getTo() {
        return to;
    }
}
