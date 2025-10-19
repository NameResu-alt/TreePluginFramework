package org.treepluginframework.meta_events.structure;

import java.util.UUID;

public class TPFStructureStartMetaEvent extends TPFStructureMetaEvent{
    public TPFStructureStartMetaEvent(UUID tpfUUID, String eventDescription) {
        super(tpfUUID, eventDescription);
    }
}
