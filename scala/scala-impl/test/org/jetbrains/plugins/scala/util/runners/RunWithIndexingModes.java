package org.jetbrains.plugins.scala.util.runners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @see MultipleScalaVersionsRunner
 * @see <a href="https://youtrack.jetbrains.com/issue/SCL-21849/Make-more-functionality-available-during-indexing">SCL-21849</a>
 * @see <a href="https://youtrack.jetbrains.com/articles/IJPL-A-270/Make-functionality-available-during-indexing">Make functionality available during indexing</a>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RunWithIndexingModes {
}
