package org.treepluginframework.meta_events.values;

import org.treepluginframework.meta_events.TPFMetaEvent;

import java.util.UUID;

public class TPFValueMetaEvent extends TPFMetaEvent {

    public TPFValueMetaEvent(UUID tpfUUID, String eventDescription) {
        super(tpfUUID, eventDescription);
    }
}
