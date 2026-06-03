package org.jetbrains.plugins.scala.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ShutDownTracker
import org.jetbrains.plugins.scala.extensions.invokeOnDispose

/**
 * Ordinary [[ShutDownTracker]] is not enough cause it leads to lambda leaks
 * on Scala Plugin unloading (see https://youtrack.jetbrains.com/issue/SCL-16809).<br>
 * To avoid such leaks [[UnloadAwareDisposable.scalaPluginDisposable]] is used.
 *
 * However, during tests run, application is not properly disposed and scalaPluginDisposable is not disposed.<br>
 * So in tests we fallback to [[ShutDownTracker]].
 * It's fine since plugin unloading is not used in tests and there will be no leaks.
 *
 * @note
 * scalaPluginDisposable is disposed in [[com.intellij.openapi.application.impl.ApplicationImpl#disposeContainer()]]
 * which is called (in particular) from [[com.intellij.testFramework.TestApplicationManager]]
 * via `disposeApplicationAndCheckForLeaks` method.<br>
 * To call this method we would need to inject some test listener which would catch "all tests finished"
 * (see [[org.junit.runner.notification.RunListener#testRunFinished(org.junit.runner.Result)]].
 * However this is not easy to support for both local test running and running tests from sbt ('''though possible''')
 *
 * @note
 * In IDEA platform there is utility class `_LastInSuiteTest` which properly disposes application.<br>
 * But it is manually injected in some test runners. See for example com.intellij.javascript.debugger.DebugTestSuite
 * or android tests using com.android.tools.tests.LeakCheckerRule.
 * Those tests use manual enumeration of test suites via `@Suite.SuiteClasses`.
 */
object ScalaShutDownTracker {

  def registerShutdownTask(runnable: Runnable): Unit =
    if (ApplicationManager.getApplication.isUnitTestMode) {
      ShutDownTracker.getInstance().registerShutdownTask(runnable)
    }
    else {
      invokeOnDispose(UnloadAwareDisposable.scalaPluginDisposable)(runnable.run())
    }
}
