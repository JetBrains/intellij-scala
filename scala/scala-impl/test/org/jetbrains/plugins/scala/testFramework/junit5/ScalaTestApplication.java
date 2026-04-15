package org.jetbrains.plugins.scala.testFramework.junit5;

import com.intellij.testFramework.junit5.fixture.TestFixtures;
import com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Does the same thing as {@link com.intellij.testFramework.junit5.TestApplication} except
 * stopping the test application instance right after the final JUnit 5/6 test has completed.
 * This is necessary because we have a mix of JUnit 3, 4 and 5/6 tests running in the same test run.
 * The platform also has this same challenge when running the tests using Bazel, and they selectively
 * disable the tear down of the application instance. Unfortunately, this is done by detecting some
 * Bazel-specific environment variables. If we set these environment variables ourselves, we get some
 * random test failures.
 */
// TODO: Contribute a change to the IntelliJ Platform JUnit 5 test framework to disable the unwanted behaviour
//       in any build tool and remove this code.
@TestOnly
@Target(ElementType.TYPE)
@ExtendWith({ScalaTestApplicationExtension.class, TestApplicationLeakTrackerExtension.class})
@TestFixtures
@Retention(RetentionPolicy.RUNTIME)
public @interface ScalaTestApplication {
}
