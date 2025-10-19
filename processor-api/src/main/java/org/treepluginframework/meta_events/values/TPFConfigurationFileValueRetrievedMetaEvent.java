package org.treepluginframework.meta_events.values;

import java.util.UUID;

public class TPFConfigurationFileValueRetrievedMetaEvent extends TPFValueMetaEvent{
    private final String fileName;
    private final String location;
    private final boolean retrievalFailed;

    public TPFConfigurationFileValueRetrievedMetaEvent(UUID tpfUUID, String eventDescription, String fileName, String location, boolean retrievalFailed) {
        super(tpfUUID, eventDescription);

        this.fileName = fileName;
        this.location = location;
        this.retrievalFailed = retrievalFailed;
    }
}
