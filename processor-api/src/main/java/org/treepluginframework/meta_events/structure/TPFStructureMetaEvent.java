package org.treepluginframework.meta_events.structure;

import org.treepluginframework.meta_events.TPFMetaEvent;

import java.util.UUID;

public class TPFStructureMetaEvent extends TPFMetaEvent {
    public TPFStructureMetaEvent(UUID tpfUUID, String eventDescription) {
        super(tpfUUID, eventDescription);
    }
}
