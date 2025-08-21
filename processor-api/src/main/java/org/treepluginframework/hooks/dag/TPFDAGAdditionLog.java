package org.treepluginframework.hooks.dag;

import org.treepluginframework.hooks.TPFEventLog;

import java.util.UUID;

/*
    When an object is added to the DAG during startup(initial tree)
 */
public class TPFDAGAdditionLog extends TPFEventLog {
    UUID dagUUID;

}
