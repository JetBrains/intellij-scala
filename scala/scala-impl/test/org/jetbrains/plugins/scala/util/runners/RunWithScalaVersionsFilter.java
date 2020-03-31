package org.jetbrains.plugins.scala.util.runners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to filter out all generated cases based on RunWithScalaVersions
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface RunWithScalaVersionsFilter {

    TestScalaVersion[] value() default {};
}
