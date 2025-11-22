package org.treepluginframework.meta_events.structure;

import java.util.UUID;

public class TPFStructureStartMetaEvent extends TPFStructureMetaEvent<TPFStructureStartMetaEvent>{

    public TPFStructureStartMetaEvent(UUID tpfUUID, String eventDescription, UUID dagUUID) {
        super(tpfUUID, eventDescription, dagUUID, 0);

    }
}
