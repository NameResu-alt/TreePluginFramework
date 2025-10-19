package org.treepluginframework.meta_events.event_propagation;

import org.treepluginframework.meta_events.TPFMetaEvent;
import org.treepluginframework.meta_events.dag.TPFDAGMetaEvent;
import org.treepluginframework.meta_events.dag.node_data.DAGNodeMetadata;

import java.util.UUID;

public class TPFEventPropagationMetaEvent extends TPFDAGMetaEvent {

    private final UUID eventUUID;

    public TPFEventPropagationMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, int version, UUID eventUUID) {
        super(tpf_uuid, eventDescription, dagUUID, version);

        this.eventUUID = eventUUID;

    }
}
