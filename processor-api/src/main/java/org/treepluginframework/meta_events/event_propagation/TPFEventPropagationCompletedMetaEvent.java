package org.treepluginframework.meta_events.event_propagation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class TPFEventPropagationCompletedMetaEvent extends TPFEventPropagationMetaEvent<TPFEventPropagationCompletedMetaEvent>{
    private final boolean cancelled;
    private final LinkedHashSet<UUID> visitedNodes;

    public TPFEventPropagationCompletedMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version, UUID eventUUID, Class<?> eventClass, boolean cancelled, LinkedHashSet<UUID> visitedNodes) {
        super(tpf_uuid, eventDescription, dagUUID, version, eventUUID, eventClass);
        this.cancelled = cancelled;
        this.visitedNodes = visitedNodes;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public Set<UUID> getVisitedNodes() {
        return visitedNodes;
    }
}
