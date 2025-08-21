package org.treepluginframework.hooks.values;

import org.treepluginframework.hooks.TPFEventLog;

/*
    When TPFValueRepository takes a value from a file
 */
public class TPFValueRetrievedLog extends TPFEventLog {
    public String globalFile;
    public String configurationFile;
    public String location;
    public String value;
}
