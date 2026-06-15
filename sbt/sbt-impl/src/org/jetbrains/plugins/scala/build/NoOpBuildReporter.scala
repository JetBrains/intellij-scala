package org.jetbrains.plugins.scala.build

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.build.issue.BuildIssue
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildMessages.EventId

import java.nio.file.Path

abstract class NoOpBuildReporter extends BuildReporter {
  override def start(): Unit = ()

  override def finish(messages: BuildMessages): Unit = ()

  override def finishWithFailure(err: Throwable): Unit = ()

  override def finishCanceled(): Unit = ()

  override def warning(@Nls message: String, position: Option[FilePosition]): Unit = ()

  override def warning(@Nls message: String, position: Option[FilePosition], @Nls details: String): Unit = ()

  override def warning(issue: BuildIssue): Unit = ()

  override def warning(
    @Nls message: String,
    position: Option[FilePosition],
    @Nls details: String,
    navigatable: Option[Navigatable]
  ): Unit = ()

  override def error(@Nls message: String, position: Option[FilePosition]): Unit = ()

  override def info(@Nls message: String, position: Option[FilePosition]): Unit = ()

  override def info(issue: BuildIssue): Unit = ()

  override def clear(file: Path): Unit = ()

  override def log(@Nls message: String): Unit = ()

  override def logErr(@Nls message: String): Unit = ()

  override def startTask(eventId: EventId, parent: Option[EventId], @Nls message: String, time: Long): Unit = ()

  override def progressTask(eventId: EventId, total: Long, progress: Long, unit: String, @Nls message: String, time: Long): Unit = ()

  override def finishTask(eventId: EventId, @Nls message: String, result: EventResult, time: Long): Unit = ()
}
