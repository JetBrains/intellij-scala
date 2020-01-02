package org.jetbrains.plugins.scala.build

import com.intellij.build._
import com.intellij.build.events._
import org.jetbrains.plugins.scala.build.BuildMessages.EventId

trait BuildReporter {
  def start(): Unit

  def finish(messages: BuildMessages): Unit
  def finishWithFailure(err: Throwable): Unit
  def finishCanceled(): Unit

  def warning(message: String, position: Option[FilePosition]): Unit
  def error(message: String, position: Option[FilePosition]): Unit
  def info(message: String, position: Option[FilePosition]): Unit

  def log(message: String): Unit
}

trait TaskReporter {
  def startTask(eventId: EventId, parent: Option[EventId], message: String, time: Long = System.currentTimeMillis()): Unit
  def progressTask(eventId: EventId, total: Long, progress: Long, unit: String, message: String, time: Long = System.currentTimeMillis()): Unit
  def finishTask(eventId: EventId, message: String, result: EventResult, time: Long = System.currentTimeMillis()): Unit
}

trait BuildTaskReporter extends BuildReporter with TaskReporter

