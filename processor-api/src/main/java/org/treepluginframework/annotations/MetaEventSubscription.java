package org.treepluginframework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


//Ends up using the exact same event.json file to cache these methods as well.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MetaEventSubscription {
    /***
     When multiple methods have the same method, priority determines the order in which the methods are ran. Ignored if not the case.
     * Necessary if multiple methods use the same event.
     * Higher priority methods come first in execution
     * @return
     */
    int priority() default 0;

    /***
     * Whether or not the method can accept SubClasses of the stated event type.
     * For abstract classes, this is automatically set to true
     * @return boolean
     */
    boolean useSubClasses() default false;

    /***
     * If a method expects an adapter type that is incompatible with the adapter accompanying the event, the method will be skipped.
     * When this flag is true, the framework will log a warning indicating the mismatch; when false, the method is silently ignored.
     * @return
     */
    boolean notifyOnAdapterMismatch() default true;
}
