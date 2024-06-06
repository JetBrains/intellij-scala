package org.jetbrains.plugins.scala.build

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.sbt.SbtBundle

import java.io.File

class IndicatorReporter(indicator: ProgressIndicator) extends BuildReporter {
  //text with ANSI escape sequences can come from sbt output (see SCL-20873)
  private val myAnsiEscapeDecoder = new AnsiEscapeDecoder

  override def start(): Unit = {
    indicator.setText(SbtBundle.message("report.build.running"))
  }

  override def finish(messages: BuildMessages): Unit = {
    //noinspection ScalaExtractStringToBundle
    indicator.setText2("")

    if (messages.errors.isEmpty)
      indicator.setText(SbtBundle.message("report.build.completed"))
    else
      indicator.setText(SbtBundle.message("report.build.failed"))
  }

  override def finishWithFailure(err: Throwable): Unit = {
    indicator.setText(SbtBundle.message("report.failed.with.message", err.getMessage))
  }

  override def finishCanceled(): Unit = {
    indicator.setText(SbtBundle.message("report.canceled"))
  }


  override def warning(message: String, position: Option[FilePosition]): Unit = {
    indicator.setText(SbtBundle.message("report.warning.with.message", message))
    indicator.setText2(positionString(position))
  }

  override def error(message: String, position: Option[FilePosition]): Unit = {
    indicator.setText(SbtBundle.message("report.error.with.message", message))
    indicator.setText2(positionString(position))
  }

  override def log(message: String): Unit = {
    indicator.setText(SbtBundle.message("report.building"))
    myAnsiEscapeDecoder.escapeText(message, ProcessOutputTypes.STDOUT, (messageUnescaped, _) => {
      //noinspection ReferencePassedToNls
      indicator.setText2(messageUnescaped)
    })
  }

  override def info(message: String, position: Option[FilePosition]): Unit = {
    myAnsiEscapeDecoder.escapeText(message, ProcessOutputTypes.STDOUT, (messageUnescaped, _) => {
      //noinspection ReferencePassedToNls
      indicator.setText(messageUnescaped)
    })
    indicator.setText2(positionString(position))
  }

  private def positionString(position: Option[FilePosition]) = {
    position.fold("") { pos =>
      s"${pos.getFile} [${pos.getStartLine}:${pos.getStartColumn}]"
    }
  }

  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = ()
  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = ()
  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit = ()
  override def clear(file: File): Unit = ()
}