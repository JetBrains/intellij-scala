package org.jetbrains.plugins.scala.build

import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult

import java.io.{File, PrintStream}

class ConsoleReporter(
  val name: String,
  out: PrintStream
) extends BuildReporter {

  def this(name: String) = {
    this(name, System.out)
  }

  //Convenient method to be able to debug printed content in single place
  private def myPrintln(s: String): Unit = {
    out.println(s)
  }

  override def start(): Unit =
    myPrintln(s"Started task: $name")

  override def finish(messages: BuildMessages): Unit = {
    myPrintln(s"Completed task $name with status ${messages.status}")
    if (messages.exceptions.nonEmpty) myPrintln("errors:\n" + messages.exceptions.map(_.getMessage).mkString("\n"))
    if (messages.errors.nonEmpty) myPrintln("errors:\n" + messages.errors.mkString("\n"))
    if (messages.warnings.nonEmpty) myPrintln("warnings:\n" + messages.warnings.mkString("\n"))
  }

  override def finishWithFailure(err: Throwable): Unit = {
    myPrintln(s"Failed task $name:")
    err.printStackTrace()
  }

  override def finishCanceled(): Unit =
    myPrintln(s"Canceled task $name")

  override def warning(message: String, position: Option[FilePosition]): Unit =
    myPrintln(s"[$name][WARNING] $message" + positionString(position))

  override def error(message: String, position: Option[FilePosition]): Unit =
    myPrintln(s"[$name][ERROR] $message" + positionString(position))

  override def info(message: String, position: Option[FilePosition]): Unit =
    myPrintln(s"[$name][INFO] $message" + positionString(position))


  override def log(message: String): Unit =
    myPrintln(s"[$name] $message")


  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = {
    val underParent = parent.map(p => s" with parent: ${p.id}")
    myPrintln(s"[$name] task started with eventId: ${eventId.id}$underParent. time: $time. message: $message")
  }

  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit =
    myPrintln(s"[$name] task ${eventId.id} progress: $progress/$total units. time: $time. message: $message")


  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit =
    myPrintln(s"[$name] task ${eventId.id} finish. time: $time. message: $message. result: $result")

  override def clear(file: File): Unit = ()

  private def positionString(position: Option[FilePosition]) =
    position.map("at" + _.toString).getOrElse("")
}
