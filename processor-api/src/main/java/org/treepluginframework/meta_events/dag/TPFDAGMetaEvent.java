package org.treepluginframework.meta_events.dag;

import org.treepluginframework.meta_events.TPFMetaEvent;

import java.util.UUID;

public abstract class TPFDAGMetaEvent<T extends TPFDAGMetaEvent<T>> extends TPFMetaEvent<T> {
    private final UUID dagUUID;
    private final long version;

    public TPFDAGMetaEvent(UUID tpf_uuid, String eventDescription, UUID dagUUID, long version) {
        super(tpf_uuid, eventDescription);
        this.dagUUID = dagUUID;
        this.version = version;
    }

    public UUID getDagUUID() {
        return dagUUID;
    }

    public long getVersion() {
        return version;
    }
}
