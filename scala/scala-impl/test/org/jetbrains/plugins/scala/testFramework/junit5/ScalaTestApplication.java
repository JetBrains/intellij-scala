package org.jetbrains.plugins.scala.testFramework.junit5;

import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compatibility wrapper over {@link TestApplication}.
 *
 * @deprecated Use {@link TestApplication} directly.
 */
@TestOnly
@Deprecated(since = "2026.2", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "2026.3")
@Target(ElementType.TYPE)
@TestApplication
@Retention(RetentionPolicy.RUNTIME)
public @interface ScalaTestApplication {
}
