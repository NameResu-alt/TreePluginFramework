package org.treepluginframework.meta_events.structure;

import java.util.UUID;

/*
    When an object gets an event subscription added to it.
 */
public class TPFStructureEventSubscriptionAddedEvent extends TPFStructureMetaEvent {
    private final String objectClass;
    private final String subscriptionType;
    private final String methodName;

    public TPFStructureEventSubscriptionAddedEvent(UUID tpfUUID, String eventDescription, String objectClass, String subscriptionType, String methodName) {
        super(tpfUUID, eventDescription);

        this.objectClass = objectClass;
        this.subscriptionType = subscriptionType;
        this.methodName = methodName;
    }
}
