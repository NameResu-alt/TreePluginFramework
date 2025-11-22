package org.treepluginframework.meta_events.structure;

import java.util.UUID;

/*
    When an object gets an event subscription added to it.
 */
public class TPFStructureEventSubscriptionRegisteredEvent extends TPFStructureMetaEvent<TPFStructureEventSubscriptionRegisteredEvent> {
    private final Class<?> objectClass;
    private final Class<?> originClass;
    private final Class<?> eventType;
    private final String methodName;
    private final Class<?> adapterType;


    public TPFStructureEventSubscriptionRegisteredEvent(UUID tpfUUID, String eventDescription, Class<?> objectClass, Class<?> originClass, Class<?> eventType, String methodName, Class<?> adapterType) {
        super(tpfUUID, eventDescription,null,-1);
        this.objectClass = objectClass;
        this.originClass = originClass;
        this.eventType = eventType;
        this.methodName = methodName;
        this.adapterType = adapterType;
    }

    public Class<?> getObjectClass() {
        return objectClass;
    }

    public Class<?> getOriginClass() {
        return originClass;
    }

    public Class<?> getEventType() {
        return eventType;
    }

    public String getMethodName() {
        return methodName;
    }

    public Class<?> getAdapterType() {
        return adapterType;
    }
}
