package org.treepluginframework.meta_events.event_propagation;

import java.util.Set;
import java.util.UUID;

public class TPFEventPropagationCompletedMetaEvent extends TPFEventPropagationMetaEvent{
    private final boolean cancelled;
    private final Set<UUID> visitedNodes;

    public TPFEventPropagationCompletedMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, int version, UUID eventUUID, boolean cancelled, Set<UUID> visitedNodes) {
        super(tpf_uuid, eventDescription, dagUUID, version, eventUUID);
        this.cancelled = cancelled;
        this.visitedNodes = visitedNodes;
    }
}
