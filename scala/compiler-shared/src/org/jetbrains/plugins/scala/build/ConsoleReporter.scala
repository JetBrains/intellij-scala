package org.jetbrains.plugins.scala.build
import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult

import java.io.File

class ConsoleReporter(val name: String) extends BuildReporter {

  override def start(): Unit =
    println(s"Started task: $name")

  override def finish(messages: BuildMessages): Unit = {
    println(s"Completed task $name with status ${messages.status}")
    if (messages.exceptions.nonEmpty) println("errors:\n" + messages.exceptions.map(_.getMessage).mkString("\n"))
    if (messages.errors.nonEmpty) println("errors:\n" + messages.errors.mkString("\n"))
    if (messages.warnings.nonEmpty) println("warnings:\n" + messages.warnings.mkString("\n"))
  }

  override def finishWithFailure(err: Throwable): Unit = {
    println(s"Failed task $name:")
    err.printStackTrace()
  }

  override def finishCanceled(): Unit =
    println(s"Canceled task $name")

  override def warning(message: String, position: Option[FilePosition]): Unit =
    println(s"[$name][WARNING] $message" + positionString(position))

  override def error(message: String, position: Option[FilePosition]): Unit =
    println(s"[$name][ERROR] $message" + positionString(position))

  override def info(message: String, position: Option[FilePosition]): Unit =
    println(s"[$name][INFO] $message" + positionString(position))


  override def log(message: String): Unit =
    println(s"[$name] $message")


  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = {
    val underParent = parent.map(p => s" with parent: ${p.id}")
    println(s"[$name] task started with eventId: ${eventId.id}$underParent. time: $time. message: $message")
  }

  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit =
    println(s"[$name] task ${eventId.id} progress: $progress/$total units. time: $time. message: $message")


  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit =
    println(s"[$name] task ${eventId.id} finish. time: $time. message: $message. result: $result")

  override def clear(file: File): Unit = ()

  private def positionString(position: Option[FilePosition]) =
  position.map("at" + _.toString).getOrElse("")

}
