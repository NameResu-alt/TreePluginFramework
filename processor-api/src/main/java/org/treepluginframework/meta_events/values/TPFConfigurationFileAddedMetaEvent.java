package org.treepluginframework.meta_events.values;

import java.util.UUID;

public class TPFConfigurationFileAddedMetaEvent extends TPFValueMetaEvent{
    private final boolean isGlobal;
    private final String fileName;

    public TPFConfigurationFileAddedMetaEvent(UUID tpfUUID, String eventDescription, boolean isGlobal, String fileName) {
        super(tpfUUID, eventDescription);

        this.isGlobal = isGlobal;
        this.fileName = fileName;
    }
}
