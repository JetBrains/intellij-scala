package org.jetbrains.plugins.scala.testFramework.junit5

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.cleanApplicationState
import com.intellij.testFramework.common.initTestApplication
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Does the same thing as [com.intellij.testFramework.junit5.impl.TestApplicationExtension] except
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
class ScalaTestApplicationExtension : BeforeAllCallback, AfterEachCallback {
  override fun beforeAll(context: ExtensionContext) {
    context.testApplication().getOrThrow()
  }

  override fun afterEach(context: ExtensionContext) {
    ApplicationManager.getApplication().cleanApplicationState()
  }
}

@TestOnly
private fun ExtensionContext.testApplication(): Result<Unit> {
  val store = root.getStore(ExtensionContext.Namespace.GLOBAL)
  @Suppress("UNCHECKED_CAST", "UnstableApiUsage")
  return store.computeIfAbsent("application", { initTestApplication() }) as Result<Unit>
}
