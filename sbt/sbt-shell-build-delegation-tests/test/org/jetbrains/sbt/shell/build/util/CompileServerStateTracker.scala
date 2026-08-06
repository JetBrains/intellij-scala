package org.jetbrains.sbt.shell.build.util

import com.intellij.openapi.Disposable
import com.intellij.util.ExceptionUtil
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.junit.Assert.fail

/**
 * Tracks Scala Compile Server state around sbt-shell build delegation tests.
 *
 * These tests are interested in whether sbt-shell build delegation accidentally
 * asks JPS/Scala compilation infrastructure to start the compile server. The
 * final server process state alone is not enough: in the shared TeamCity JVM,
 * a server may have been leaked by an earlier test before this test starts.
 */
final class CompileServerStateTracker(logStep: String => Unit) {

  @volatile private var compileServerWasRunningBeforeTestStart: Boolean = false
  @volatile private var compileServerStartStackTraceBeforeTestStart: Option[Throwable] = None
  @volatile private var compileServerStartRequestsDuringTest: Option[CompileServerLauncher.ServerStartRequestsWatcherForTests] = None

  def markCompileServerStateBeforeTestStart(testRootDisposable: Disposable): Unit = {
    // Capture the state before test setup stops a possibly leaked server.
    // Later assertions still need to know whether a running server belonged to this test or was already present when the test began.
    val state = CompileServerLauncher.captureRunningServerStateForTests
    compileServerWasRunningBeforeTestStart = state.wasRunning
    compileServerStartStackTraceBeforeTestStart = state.startStackTrace
    compileServerStartRequestsDuringTest = Some(
      CompileServerLauncher.watchServerStartRequestsForTests(testRootDisposable)
    )
  }

  def assertCompileServerIsNotRunning(): Unit = {
    val runningState = CompileServerLauncher.captureRunningServerStateForTests
    val wasAlreadyRunningBeforeSetup = compileServerWasRunningBeforeTestStart
    val isRunningAfterTest = runningState.wasRunning
    val startRequestsDuringTest = compileServerStartRequestsDuringTest.toSeq.flatMap(_.requests)

    if (startRequestsDuringTest.isEmpty) {
      if (wasAlreadyRunningBeforeSetup) {
        // If the serer was already running before the test, it's fine, warn and return
        val stacktraceText = renderStackTraceBlock(renderBeforeTestSetupStackTraceSection().toSeq)
        logStep(
          s"""WARNING: Scala Compile Server was already running before test setup; no start requests were sent during this test, so the pre-existing server state is ignored.
             |$stacktraceText""".stripMargin
        )
        return
      }


      if (isRunningAfterTest) {
        // fail the test later
        // This means the compiler server was somehow started without tracking the `startRequestsDuringTest`
      } else {
        return
      }
    }

    val stateMessage =
      if (startRequestsDuringTest.nonEmpty) {
        // A request is enough to fail the test even if a server was already running. In that case no new process
        // may be started, but the build still reached the JPS/Scala compile-server path we want to avoid.
        val preExistingState =
          if (wasAlreadyRunningBeforeSetup)
            " The server was already running before test setup, so pre-existing process state is ignored."
          else
            ""
        s"Scala Compile Server start requests were sent during test execution.$preExistingState"
      } else {
        "Scala Compile Server is running after test execution."
      }

    val stateDetails =
      s"wasRunningBeforeTestSetup=$wasAlreadyRunningBeforeSetup, isRunningAfterTest=$isRunningAfterTest, startRequestsDuringTest=${startRequestsDuringTest.size}"

    val startRequestTraces = startRequestsDuringTest.zipWithIndex.map { case (request, index) =>
      val stackTraceText = ExceptionUtil.getThrowableText(request.stackTrace).trim
      s"Compile Server start request #${index + 1} for project '${request.projectName}':\n$stackTraceText"
    }

    val traces = Seq(
      Some(startRequestTraces).filter(_.nonEmpty).map(_.mkString("\n\n")),
      renderStackTraceSection("Compile Server start stack trace captured BEFORE test setup", compileServerStartStackTraceBeforeTestStart),
      renderStackTraceSection("Compile Server start stack trace captured DURING test execution", runningState.startStackTrace),
    ).flatten

    fail(
      s"""$stateMessage
         |$stateDetails
         |${renderStackTraceBlock(traces)}""".stripMargin.trim
    )
  }

  private def renderBeforeTestSetupStackTraceSection(): Option[String] =
    renderStackTraceSection("Compile Server start stack trace captured BEFORE test setup", compileServerStartStackTraceBeforeTestStart)

  private def renderStackTraceBlock(traceSections: Seq[String]): String = {
    val tracesText =
      if (traceSections.nonEmpty) traceSections.mkString("\n\n", "\n\n", "")
      else ""

    s"""STACK TRACE START
       |$tracesText
       |STACK TRACE END""".stripMargin
  }

  private def renderStackTraceSection(header: String, throwable: Option[Throwable]): Option[String] = {
    val stacktraceText = throwable
      .map(ExceptionUtil.getThrowableText)
      .map(_.trim)
      .filter(_.nonEmpty)
    stacktraceText.map(stack => s"$header:\n$stack")
  }
}
