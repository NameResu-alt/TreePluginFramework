package org.treepluginframework.meta_events;

import java.util.UUID;

public abstract class TPFMetaEvent {
    private final UUID tpfUUID;
    //The UUID of this meta even, for reference later.
    private final UUID metaEventUUID = UUID.randomUUID();
    private final String eventDescription;
    private final long timestamp = System.currentTimeMillis();

    public TPFMetaEvent(UUID tpfUUID, String eventDescription){
        this.tpfUUID = tpfUUID;
        this.eventDescription = eventDescription;
    }
}
