package org.treepluginframework.meta_events.structure;

import org.treepluginframework.meta_events.TPFMetaEvent;

import java.util.UUID;

public class TPFStructureCompletedMetaEvent extends TPFStructureMetaEvent {
    private final UUID dag_uuid;
    public TPFStructureCompletedMetaEvent(UUID tpfUUID, String eventDescription, UUID dag_uuid) {
        super(tpfUUID, eventDescription);

        this.dag_uuid = dag_uuid;
    }
}
