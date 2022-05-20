package org.jetbrains.plugins.scala.build

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import org.jetbrains.plugins.scala.build.BuildMessages.EventId

import java.io.File

class CompositeReporter(reporters: BuildReporter*) extends BuildReporter {
  override def start(): Unit =
    reporters.foreach(_.start())

  override def finish(messages: BuildMessages): Unit =
    reporters.foreach(_.finish(messages))

  override def finishWithFailure(err: Throwable): Unit =
    reporters.foreach(_.finishWithFailure(err))

  override def finishCanceled(): Unit =
    reporters.foreach(_.finishCanceled())

  override def warning(message: String, position: Option[FilePosition]): Unit =
    reporters.foreach(_.warning(message, position))

  override def error(message: String, position: Option[FilePosition]): Unit =
    reporters.foreach(_.error(message, position))

  override def info(message: String, position: Option[FilePosition]): Unit =
    reporters.foreach(_.info(message,position))

  override def log(message: String): Unit =
    reporters.foreach(_.log(message))

  override def startTask(eventId: EventId, parent: Option[EventId], message: String, time: Long): Unit =
    reporters.foreach(_.startTask(eventId, parent, message, time))

  override def progressTask(eventId: EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit =
    reporters.foreach(_.progressTask(eventId, total, progress, unit, message, time))

  override def finishTask(eventId: EventId, message: String, result: EventResult, time: Long): Unit =
    reporters.foreach(_.finishTask(eventId, message, result, time))

  override def clear(file: File): Unit =
    reporters.foreach(_.clear(file))

  def compose(reporter: BuildReporter) =
  new CompositeReporter(reporters :+ reporter : _*)

  /** Clear any messages associated with file. */
}
