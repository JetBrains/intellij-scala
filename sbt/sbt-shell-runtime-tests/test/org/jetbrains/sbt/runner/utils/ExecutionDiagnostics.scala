package org.jetbrains.sbt.runner.utils

import org.jetbrains.sbt.process.SbtProcessOutputDiagnosticsCollector

private[runner] object ExecutionDiagnostics {
  def withDiagnostics[T](
    executionObserver: => Option[RunConfigurationExecutionObserver] = None,
  )(body: => T): T =
    try {
      body
    } catch {
      case error: AssertionError =>
        val enrichedError = new AssertionError(
          s"""${error.getMessage}
             |
             |Execution diagnostics:
             |${diagnosticsSnapshot(executionObserver)}""".stripMargin
        )
        enrichedError.initCause(error)
        throw enrichedError
    }

  def clearSbtProcessOutput(): Unit =
    SbtProcessOutputDiagnosticsCollector.clearSharedProcessOutput()

  def sbtProcessOutputSnapshot: String =
    SbtProcessOutputDiagnosticsCollector.sharedProcessOutput

  private def diagnosticsSnapshot(executionObserver: Option[RunConfigurationExecutionObserver]): String =
    executionObserver
      .map(_.diagnosticsSnapshot)
      .getOrElse(DiagnosticOutputFormatter.section("SBT process output", sbtProcessOutputSnapshot))
}
