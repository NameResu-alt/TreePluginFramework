package org.treepluginframework.meta_events.dag;

import org.treepluginframework.meta_events.TPFMetaEvent;

import java.util.UUID;

public abstract class TPFDAGMetaEvent extends TPFMetaEvent {
    private final UUID dagUUID;
    private final int version;

    public TPFDAGMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, int version) {
        super(tpf_uuid, eventDescription);
        this.dagUUID = dagUUID;
        this.version = version;
    }
}
