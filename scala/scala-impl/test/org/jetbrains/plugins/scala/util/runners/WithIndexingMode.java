package org.jetbrains.plugins.scala.util.runners;

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;

import java.lang.annotation.*;

/**
 * Enables one of the indexing modes for each test: smart mode or dumb mode with empty, partial or full index.
 *
 * @see IndexingMode
 * @see org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
 * @see <a href="https://youtrack.jetbrains.com/issue/SCL-21849/Make-more-functionality-available-during-indexing">SCL-21849</a>
 * @see <a href="https://youtrack.jetbrains.com/articles/IJPL-A-270/Make-functionality-available-during-indexing">Make functionality available during indexing</a>
 */
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WithIndexingMode {
    IndexingMode mode();

    String reason() default "";
}
