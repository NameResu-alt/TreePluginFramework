package org.treepluginframework;

import org.treepluginframework.annotations.EventSubscription;
import org.treepluginframework.annotations.MetaEventSubscription;
import org.treepluginframework.annotations.TPFMetaEventListener;
import org.treepluginframework.annotations.TPFNode;
import org.treepluginframework.meta_events.TPFMetaEvent;

public class MetaTest {

    @MetaEventSubscription
    public void test(TPFMetaEvent event){
        System.out.println("Got meta event: " + event.getClass().getCanonicalName());
    }
}
