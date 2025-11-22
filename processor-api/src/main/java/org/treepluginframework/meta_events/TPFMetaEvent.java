package org.treepluginframework.meta_events;

import java.util.UUID;

public abstract class TPFMetaEvent<T extends TPFMetaEvent<T>> {
    private final UUID tpfUUID;
    //The UUID of this meta even, for reference later.
    //NOTE: This is not the same as the UUID that adapter get.
    //This UUID is to distinguish this specific meta event.
    private final UUID metaEventUUID = UUID.randomUUID();
    private final String eventDescription;
    private final long timestamp = System.currentTimeMillis();

    public TPFMetaEvent(UUID tpfUUID, String eventDescription){
        this.tpfUUID = tpfUUID;
        this.eventDescription = eventDescription;
    }

    public UUID getTpfUUID() {
        return tpfUUID;
    }

    public UUID getMetaEventUUID() {
        return metaEventUUID;
    }

    public String getEventDescription() {
        return eventDescription;
    }

    public long getTimestamp() {
        return timestamp;
    }

    //public abstract T cloneEvent();
}
