package org.treepluginframework.meta_events.event_propagation;

import org.treepluginframework.meta_events.dag.TPFDAGMetaEvent;

import java.util.UUID;

public abstract class TPFEventPropagationMetaEvent<T extends TPFEventPropagationMetaEvent<T>> extends TPFDAGMetaEvent<T> {

    private final UUID eventUUID;
    private final Class<?> eventClass;

    public TPFEventPropagationMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version, UUID eventUUID, Class<?> eventClass) {
        super(tpf_uuid, eventDescription, dagUUID, version);

        this.eventUUID = eventUUID;
        this.eventClass = eventClass;
    }

    public UUID getEventUUID() {
        return eventUUID;
    }

    public Class<?> getEventClass(){
        return eventClass;
    }
}
