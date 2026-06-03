package org.jetbrains.sbt.actions

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}

import java.nio.file.Path
import scala.collection.mutable

private[sbt] final class GenerateManagedSourcesReporter extends BuildReporter {
  private val outputLinesBuffer: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
  private var splitLine: Boolean = false

  private val logLevelPrefixes: Array[String] = Array("[debug]", "[info]", "[warn]", "[error]")

  override def start(): Unit = {}

  override def finish(messages: BuildMessages): Unit = {}

  override def finishWithFailure(err: Throwable): Unit = {}

  override def finishCanceled(): Unit = {}

  override def warning(message: String, position: Option[FilePosition]): Unit = {}

  override def error(message: String, position: Option[FilePosition]): Unit = {}

  override def info(message: String, position: Option[FilePosition]): Unit = {}

  override def clear(file: Path): Unit = {}

  // TODO add custom error logging logic if when necessary
  override def logErr(message: String): Unit =
    log(message)

  override def log(message: String): Unit = {
    if (splitLine && logLevelPrefixes.exists(message.startsWith)) {
      // There are lines printed by sbt after which user input is expected. These do not end with a newline. We do not
      // want to treat them like split lines.
      splitLine = false
    }

    // If the last line was split, we need to concatenate the current line to it.
    val prefix = if (splitLine) outputLinesBuffer.last else ""
    val newLine = (prefix ++ message).trim

    if (splitLine) {
      outputLinesBuffer.update(outputLinesBuffer.length - 1, newLine)
    } else {
      outputLinesBuffer += newLine
    }

    // A line is split if it doesn't end with a newline character.
    splitLine = !message.endsWith("\n")
  }

  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = {}

  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = {}

  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit = {}

  def outputLines: Seq[String] = outputLinesBuffer.toSeq
}
