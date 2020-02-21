package org.jetbrains.plugins.scala.build

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.openapi.progress.ProgressIndicator

class IndicatorReporter(indicator: ProgressIndicator) extends BuildReporter {
  override def start(): Unit = {
    indicator.setText("build running ...")
  }

  override def finish(messages: BuildMessages): Unit = {
    indicator.setText2("")

    if (messages.errors.isEmpty)
      indicator.setText("build completed")
    else
      indicator.setText("build failed")
  }

  override def finishWithFailure(err: Throwable): Unit = {
    indicator.setText(s"errored: ${err.getMessage}")
  }

  override def finishCanceled(): Unit = {
    indicator.setText("canceled")
  }


  override def warning(message: String, position: Option[FilePosition]): Unit = {
    indicator.setText(s"WARNING: $message")
    indicator.setText2(positionString(position))
  }

  override def error(message: String, position: Option[FilePosition]): Unit = {
    indicator.setText(s"ERROR: $message")
    indicator.setText2(positionString(position))
  }

  override def log(message: String): Unit = {
    indicator.setText("building ...")
    indicator.setText2(message)
  }

  override def info(message: String, position: Option[FilePosition]): Unit = {
    indicator.setText(message)
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
}
