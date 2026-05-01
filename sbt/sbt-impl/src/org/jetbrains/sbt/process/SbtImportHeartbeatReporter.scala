package org.jetbrains.sbt.process

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.build.BuildReporter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
 * Reports a periodic heartbeat for long-running sbt import loops and dumps current collected process output to a temp file.
 *
 * The dump file is created once per reporter instance (unique per import run) and reused on each heartbeat.
 */
private final class SbtImportHeartbeatReporter(
  processOutputCollector: Option[ProcessOutputCollector],
  heartbeatInterval: FiniteDuration,
  enabledInCurrentRun: Boolean,
) {
  import SbtImportHeartbeatReporter.*

  private val heartbeatOutputDumpFile: Option[Path] =
    if (enabledInCurrentRun) createHeartbeatOutputDumpFileInTempDir()
    else None

  private var lastHeartbeatTimeMillis: Long = 0L

  def initialize(startTimeMillis: Long): Unit =
    lastHeartbeatTimeMillis = startTimeMillis

  def reportIfDue(nowMillis: Long, startTimeMillis: Long, reporter: BuildReporter): Unit = {
    if (!enabledInCurrentRun) return
    val shouldReportHeartbeat = (nowMillis - lastHeartbeatTimeMillis) >= heartbeatInterval.toMillis
    if (!shouldReportHeartbeat) return

    val heartbeatOutputDumpPathText = dumpHeartbeatOutputAndGetPathText()
    val elapsedSeconds = (nowMillis - startTimeMillis) / 1000
    val heartbeatMessage =
      s"[sbt import] still running after ${elapsedSeconds}s; waiting for process output or completion; captured output dump: $heartbeatOutputDumpPathText"
    reporter.log(heartbeatMessage)
    println(heartbeatMessage)
    lastHeartbeatTimeMillis = nowMillis
  }

  private def dumpHeartbeatOutputAndGetPathText(): String = {
    heartbeatOutputDumpFile match {
      case Some(file) =>
        val outputText = processOutputCollector.fold("")(_.processOutput)
        Try {
          Files.writeString(
            file,
            outputText,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
          )
          file.toAbsolutePath.toString
        }.recover { case ex =>
          Log.warn(s"Failed to write sbt import heartbeat output into $file", ex)
          s"<write failed: ${file.toAbsolutePath}>"
        }.get
      case None =>
        "<not available>"
    }
  }
}

private object SbtImportHeartbeatReporter {
  private val Log: Logger = Logger.getInstance(classOf[SbtImportHeartbeatReporter])

  private def createHeartbeatOutputDumpFileInTempDir(): Option[Path] =
    Try {
      val tempDir = Path.of(System.getProperty("java.io.tmpdir"))
      Files.createDirectories(tempDir)
      Files.createTempFile(tempDir, "sbt-import-heartbeat-", ".log")
    }
      .recover { case ex =>
        Log.warn("Failed to create sbt import heartbeat dump file in system temp directory", ex)
        null
      }
      .toOption
      .flatMap(Option(_))
}
