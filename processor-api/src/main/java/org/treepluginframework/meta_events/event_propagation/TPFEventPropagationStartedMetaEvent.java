package org.treepluginframework.meta_events.event_propagation;

import java.util.UUID;

public class TPFEventPropagationStartedMetaEvent extends TPFEventPropagationMetaEvent{

    public TPFEventPropagationStartedMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, int version, UUID eventUUID) {
        super(tpf_uuid, eventDescription, dagUUID, version,eventUUID);
    }
}
