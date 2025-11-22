package org.treepluginframework.meta_events.dag.node_data;

import java.util.UUID;

public class DAGNodeReference {
    private final UUID nodeUUID;
    private final Class<?> objectClass;
    private final String nodeDescription;
    public DAGNodeReference(UUID uuid, Class<?> objectClass, String nodeDescription){
        this.nodeUUID = uuid;
        this.objectClass = objectClass;
        this.nodeDescription = nodeDescription;
    }

    public UUID getNodeUUID() {
        return nodeUUID;
    }

    public Class<?> getObjectClass() {
        return objectClass;
    }

    public String getNodeDescription() {
        return nodeDescription;
    }
}
