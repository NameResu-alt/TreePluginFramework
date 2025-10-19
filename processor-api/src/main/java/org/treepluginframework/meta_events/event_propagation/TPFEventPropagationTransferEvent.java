package org.treepluginframework.meta_events.event_propagation;

import org.treepluginframework.meta_events.dag.node_data.DAGNodeMetadata;

import java.util.UUID;

public class TPFEventPropagationTransferEvent extends TPFEventPropagationMetaEvent{
    private final DAGNodeMetadata from;
    private final DAGNodeMetadata to;

    public TPFEventPropagationTransferEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, int version, UUID eventUUID, DAGNodeMetadata from, DAGNodeMetadata to) {
        super(tpf_uuid, eventDescription, dagUUID, version,eventUUID);

        this.from = from;
        this.to = to;
    }
}
