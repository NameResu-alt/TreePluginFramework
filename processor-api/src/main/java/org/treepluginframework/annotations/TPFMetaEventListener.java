package org.treepluginframework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//If a class is annotated with TPFMetaEventListener, EventSubscription annotations, any of them, will return an error.
//Only works with MetaEventSubscription annotation.
//If a class is annotated with TPFMetaEventListener, it'll be created like the nodes of the system.
//except it won't go into the tree structure, instead it'll just become a part of a list of ForeignEventListeners.
//A class can be a foreign event listener without being annotated with TPFMetaEventListener, it'll just have to be manually added at runtime.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TPFMetaEventListener {
}
