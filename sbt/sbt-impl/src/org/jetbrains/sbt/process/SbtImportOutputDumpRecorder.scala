package org.jetbrains.sbt.process

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.sbt.project.structure.OutputType

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.Try

/**
 * Records raw sbt process output and appends it to a temp dump file in tests.
 *
 * The dump file is created once per recorder instance (unique per import run) and reused for all incoming chunks.
 */
private final class SbtImportOutputDumpRecorder(
  enabledInCurrentRun: Boolean,
) {
  import SbtImportOutputDumpRecorder.*

  // Dump file creation can fail due to filesystem/environment issues; this must not fail the main import flow.
  private val outputDumpFile: Option[Path] =
    if (enabledInCurrentRun) createOutputDumpFileInTempDir()
    else None

  private var updatesCount: Long = 0L

  /**
   * Kept in-memory as a heartbeat fallback when dump-file creation/writes fail,
   * so heartbeats can still show the latest observed process output line.
   */
  private var latestLine: String = "<no output yet>"

  def onProcessOutput(outputType: OutputType, outputTextRaw: String): Unit = {
    if (!enabledInCurrentRun) return

    val outputWithTypePrefix = s"[${outputType.name}] $outputTextRaw"
    appendToDumpFile(outputWithTypePrefix)
    updatesCount += 1
    latestLine = extractLatestLine(outputWithTypePrefix)
  }

  def outputUpdatesCount: Long = updatesCount

  def latestOutputLine: String = latestLine

  def outputDumpPathText: String =
    if (!enabledInCurrentRun) "<not available>"
    else outputDumpFile.fold("<not available>")(_.toAbsolutePath.toString)

  private def appendToDumpFile(outputChunk: String): Unit = {
    outputDumpFile.foreach(file => appendToDumpFile(file, outputChunk))
  }

  private def appendToDumpFile(outputDumpFile: Path, outputChunk: String): Unit = {
    Try {
      Files.writeString(
        outputDumpFile,
        outputChunk,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
        StandardOpenOption.WRITE
      )
    }.recover { case ex =>
      Log.warn(s"Failed to append sbt import output into dump file $outputDumpFile", ex)
    }
  }

  private def extractLatestLine(text: String): String = {
    val lines = text.linesIterator.toSeq.map(_.trim).filter(_.nonEmpty)
    val latest = lines.lastOption.getOrElse("<empty line>")
    val maxLength = 240
    if (latest.length <= maxLength) latest
    else latest.take(maxLength) + "..."
  }
}

private object SbtImportOutputDumpRecorder {
  private val Log: Logger = Logger.getInstance(classOf[SbtImportOutputDumpRecorder])

  private def createOutputDumpFileInTempDir(): Option[Path] = {
    val dirTry = Try {
      val tempDir = PathManager.getTempDir
      Files.createDirectories(tempDir)
      Files.createTempFile(tempDir, "sbt-import-heartbeat-", ".log")
    }
    dirTry
      .recover { case ex =>
        Log.warn("Failed to create sbt import output dump file in system temp directory", ex)
        null
      }
      .toOption
      .flatMap(Option(_))
  }
}
