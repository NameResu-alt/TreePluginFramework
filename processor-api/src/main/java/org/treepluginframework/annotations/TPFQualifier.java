package org.treepluginframework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface TPFQualifier {
    Class<?> specifiedClass() default Void.class;

    /***
     * Can utilize:
     * A class's fully qualified name
     * The alias that a @TPFNode has.
     * @return
     */
    String className() default "";
}
