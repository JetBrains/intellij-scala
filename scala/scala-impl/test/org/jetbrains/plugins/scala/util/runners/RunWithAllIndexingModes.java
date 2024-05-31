package org.jetbrains.plugins.scala.util.runners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <strong>ATTENTION</strong>: this annotation should be used with care as it increases test execution time significantly.
 * <p>
 * Enables multiple indexing modes for each test: smart mode and different versions of dumb mode.
 * Dumb mode indexing may be tuned with <code>@NeedsIndex</code> annotation on a class or a specific test method.
 *
 * @see TestIndexingMode
 * @see MultipleScalaVersionsRunner
 * @see com.intellij.testFramework.NeedsIndex
 * @see <a href="https://youtrack.jetbrains.com/issue/SCL-21849/Make-more-functionality-available-during-indexing">SCL-21849</a>
 * @see <a href="https://youtrack.jetbrains.com/articles/IJPL-A-270/Make-functionality-available-during-indexing">Make functionality available during indexing</a>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RunWithAllIndexingModes {
}
