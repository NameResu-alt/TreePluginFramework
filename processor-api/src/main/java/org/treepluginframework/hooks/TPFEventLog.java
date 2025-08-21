package org.treepluginframework.hooks;

import java.util.UUID;

public abstract class TPFEventLog {
    private final long timestamp = System.currentTimeMillis();
    private final UUID eventUUID = UUID.randomUUID();
}
