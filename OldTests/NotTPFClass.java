package org.treepluginframework;

import org.treepluginframework.annotations.TPFValue;

public class NotTPFClass {
    @TPFValue(location = "testing", defaultValue = "Nothing to see here, for now")
    public String checking;

    @TPFValue(location = "fail",defaultValue = "303")
    public Integer numberVal;
}
