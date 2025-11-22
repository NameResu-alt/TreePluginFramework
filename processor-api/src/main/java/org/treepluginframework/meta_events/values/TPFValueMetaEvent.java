package org.treepluginframework.meta_events.values;

import org.treepluginframework.meta_events.TPFMetaEvent;

import java.util.UUID;

public abstract class TPFValueMetaEvent<T extends TPFValueMetaEvent<T>> extends TPFMetaEvent<T> {

    public TPFValueMetaEvent(UUID tpfUUID, String eventDescription) {
        super(tpfUUID, eventDescription);
    }
}
