package org.jetbrains.plugins.scala.build

import com.intellij.build._
import com.intellij.build.events._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildMessages.EventId

trait BuildReporter {
  def start(): Unit

  def finish(messages: BuildMessages): Unit
  def finishWithFailure(err: Throwable): Unit
  def finishCanceled(): Unit

  def warning(@Nls message: String, position: Option[FilePosition]): Unit
  def error(@Nls message: String, position: Option[FilePosition]): Unit
  def info(@Nls message: String, position: Option[FilePosition]): Unit

  def log(message: String): Unit
}

trait TaskReporter {
  def startTask(eventId: EventId, parent: Option[EventId], @Nls message: String, time: Long = System.currentTimeMillis()): Unit
  def progressTask(eventId: EventId, total: Long, progress: Long, unit: String, @Nls message: String, time: Long = System.currentTimeMillis()): Unit
  def finishTask(eventId: EventId, @Nls message: String, result: EventResult, time: Long = System.currentTimeMillis()): Unit
}

trait BuildTaskReporter extends BuildReporter with TaskReporter

