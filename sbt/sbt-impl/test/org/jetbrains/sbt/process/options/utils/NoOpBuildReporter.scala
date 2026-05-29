package org.jetbrains.sbt.process.options.utils

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.build.issue.BuildIssue
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}

import java.nio.file.Path

private[options] class NoOpBuildReporter extends BuildReporter {
  override def start(): Unit = ()

  override def finish(messages: BuildMessages): Unit = ()

  override def finishWithFailure(err: Throwable): Unit = ()

  override def finishCanceled(): Unit = ()

  override def warning(message: String, position: Option[FilePosition]): Unit = ()

  override def warning(message: String, position: Option[FilePosition], details: String): Unit = ()

  override def warning(issue: BuildIssue): Unit = ()

  override def warning(message: String, position: Option[FilePosition], details: String, navigatable: Option[Navigatable]): Unit = ()

  override def error(message: String, position: Option[FilePosition]): Unit = ()

  override def info(message: String, position: Option[FilePosition]): Unit = ()

  override def info(issue: BuildIssue): Unit = ()

  override def clear(file: Path): Unit = ()

  override def log(message: String): Unit = ()

  override def logErr(message: String): Unit = ()

  override def startTask(eventId: EventId, parent: Option[EventId], message: String, time: Long): Unit = ()

  override def progressTask(eventId: EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = ()

  override def finishTask(eventId: EventId, message: String, result: EventResult, time: Long): Unit = ()
}
