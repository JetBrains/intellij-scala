package org.jetbrains.plugins.scala.build

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult

import java.io.{File, PrintStream}

class ConsoleReporter(
  val name: String,
  out: PrintStream
) extends BuildReporter {

  def this(name: String) {
    this(name, System.out)
  }

  override def start(): Unit =
    out.println(s"Started task: $name")

  override def finish(messages: BuildMessages): Unit = {
    out.println(s"Completed task $name with status ${messages.status}")
    if (messages.exceptions.nonEmpty) out.println("errors:\n" + messages.exceptions.map(_.getMessage).mkString("\n"))
    if (messages.errors.nonEmpty) out.println("errors:\n" + messages.errors.mkString("\n"))
    if (messages.warnings.nonEmpty) out.println("warnings:\n" + messages.warnings.mkString("\n"))
  }

  override def finishWithFailure(err: Throwable): Unit = {
    out.println(s"Failed task $name:")
    err.printStackTrace()
  }

  override def finishCanceled(): Unit =
    out.println(s"Canceled task $name")

  override def warning(message: String, position: Option[FilePosition]): Unit =
    out.println(s"[$name][WARNING] $message" + positionString(position))

  override def error(message: String, position: Option[FilePosition]): Unit =
    out.println(s"[$name][ERROR] $message" + positionString(position))

  override def info(message: String, position: Option[FilePosition]): Unit =
    out.println(s"[$name][INFO] $message" + positionString(position))


  override def log(message: String): Unit =
    out.println(s"[$name] $message")


  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = {
    val underParent = parent.map(p => s" with parent: ${p.id}")
    out.println(s"[$name] task started with eventId: ${eventId.id}$underParent. time: $time. message: $message")
  }

  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit =
    out.println(s"[$name] task ${eventId.id} progress: $progress/$total units. time: $time. message: $message")


  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit =
    out.println(s"[$name] task ${eventId.id} finish. time: $time. message: $message. result: $result")

  override def clear(file: File): Unit = ()

  private def positionString(position: Option[FilePosition]) =
  position.map("at" + _.toString).getOrElse("")

}
