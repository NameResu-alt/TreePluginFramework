package org.treepluginframework.hooks;

import java.util.UUID;

/*
    When an event goes from one node, to another
 */
public class TPFEventPropagationLog extends TPFEventLog{
    //If it's the first call, the origin object UUID will be null.
    public UUID originObjectUUID;
    public UUID currentObjectUUID;
}
