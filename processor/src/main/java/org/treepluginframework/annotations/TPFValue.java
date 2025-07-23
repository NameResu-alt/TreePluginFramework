package org.treepluginframework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/***
 * Take a variable from docker secrets.
 * If not, take it from a config file.
 * If not, take it from environemental variables
 */

//TPFValue can only be utilized in Resource classes, or Node classes.
//Only because I can't exactly wire it to any class that isn't part of it.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER,ElementType.FIELD})
public @interface TPFValue {
    String location();
    String defaultValue() default ""; // optional
}
