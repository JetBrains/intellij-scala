package org.jetbrains.sbt.process

import java.io.PrintStream
import scala.concurrent.duration.FiniteDuration

/**
 * Reports periodic heartbeats for long-running sbt import loops.
 *
 * It primarily exists to make long-running imports observable in tests and diagnostic runs.
 */
private final class SbtImportHeartbeatReporter(
  heartbeatInterval: FiniteDuration,
  enabledInCurrentRun: Boolean,
  outputDumpRecorder: SbtImportOutputDumpRecorder,
  out: PrintStream = System.out,
) {
  private var lastHeartbeatTimeMillis: Long = 0L
  private var outputUpdatesCountAtPreviousHeartbeat: Long = 0L

  def initialize(startTimeMillis: Long): Unit =
    lastHeartbeatTimeMillis = startTimeMillis

  def reportIfDue(nowMillis: Long, startTimeMillis: Long): Unit = {
    if (!enabledInCurrentRun) return

    val shouldReportHeartbeat = (nowMillis - lastHeartbeatTimeMillis) >= heartbeatInterval.toMillis
    if (!shouldReportHeartbeat) return

    val elapsedSeconds = (nowMillis - startTimeMillis) / 1000
    val outputUpdatesCount = outputDumpRecorder.outputUpdatesCount
    val noNewOutputSincePreviousHeartbeat = outputUpdatesCount == outputUpdatesCountAtPreviousHeartbeat
    val noNewOutputWarning =
      if (noNewOutputSincePreviousHeartbeat)
        s"[sbt import] WARNING: no new process output since previous heartbeat (~${heartbeatInterval.toSeconds}s)."
      else ""

    val heartbeatMessage = Seq(
      s"[sbt import] still running after ${elapsedSeconds}s",
      s"[sbt import] captured output path: ${outputDumpRecorder.outputDumpPathText}",
      s"[sbt import] latest output line: ${outputDumpRecorder.latestOutputLine}",
      noNewOutputWarning
    ).filter(_.nonEmpty).mkString("\n").trim

    out.println(heartbeatMessage)

    outputUpdatesCountAtPreviousHeartbeat = outputUpdatesCount
    lastHeartbeatTimeMillis = nowMillis
  }
}
