package org.treepluginframework.meta_events.structure;

import org.treepluginframework.meta_events.TPFMetaEvent;

import java.util.UUID;

public abstract class TPFStructureMetaEvent<T extends TPFStructureMetaEvent<T>> extends TPFMetaEvent<T> {
    private UUID dagUUID;
    private long version;

    public TPFStructureMetaEvent(UUID tpfUUID, String eventDescription, UUID dagUUID, long version) {
        super(tpfUUID, eventDescription);
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
