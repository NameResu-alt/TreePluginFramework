package org.treepluginframework.meta_events.values;

import java.util.UUID;

public class TPFConfigurationFileValueCachedMetaEvent extends TPFValueMetaEvent{

    private final String fileName;
    private final String location;

    public TPFConfigurationFileValueCachedMetaEvent(UUID tpfUUID, String eventDescription, String fileName, String location) {
        super(tpfUUID, eventDescription);

        this.fileName = fileName;
        this.location = location;
    }
}
