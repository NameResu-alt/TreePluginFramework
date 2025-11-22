package org.treepluginframework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


//Ends up using the exact same event.json file to cache these methods as well.

/**
 * <p>A method's subscription to a Meta Event.</p>
 *
 * Methods with this subscription must use a TPFMetaEvent descendant as their event. Not doing so will result in an error.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MetaEventSubscription {
    /**
     * When multiple methods have the same event type, priority determines the order in which the methods are ran. Ignored if not the case.
     * <p>Necessary if multiple methods use the same event.</p>
     * <p>Higher priority methods come first in execution.</p>
     * @return int
     */
    int priority() default 0;

    /// TODO: Thinking about it, I think that useSubClasses is kind of redundant.
    /// What do I mean by only being able to use the stated event type?
    /// A class that only extends the class type. So if I pass in an event of that type, but
    /// that type is a descendant of whatever is stated, it doesn't trigger the method.
    /// May be redundant, since I made it so that multiple methods can use the same event type now, just having to change priority.
    /***
     * Whether the method can accept SubClasses of the stated event type.
     * <p>For abstract classes and interfaces, this is automatically set to true</p>
     * @return boolean
     */
    boolean useSubClasses() default true;

    /***
     * If a method expects an adapter type that is incompatible with the adapter accompanying the event, the method will be skipped.
     * When this flag is true, the framework will log a warning indicating the mismatch; when false, the method is silently ignored.
     * @return
     */
    boolean notifyOnAdapterMismatch() default true;
}
