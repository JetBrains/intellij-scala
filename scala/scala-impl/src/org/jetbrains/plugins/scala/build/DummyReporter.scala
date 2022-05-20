package org.jetbrains.plugins.scala.build
import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult

import java.io.File

/** Only use this as stand-in when there's no sensible way to report something. */
class DummyReporter extends BuildReporter {
  /** Beginning of reporting. */
  override def start(): Unit = ()

  /** End of reporting. */
  override def finish(messages: BuildMessages): Unit = ()

  /** Reporting ended due to error. */
  override def finishWithFailure(err: Throwable): Unit = ()

  /** Reporting ends due to task being canceled. */
  override def finishCanceled(): Unit = ()

  /** Show warning message. */
  override def warning(message: String, position: Option[FilePosition]): Unit = ()

  /** Show error message. */
  override def error(message: String, position: Option[FilePosition]): Unit = ()

  /** Show message. */
  override def info(message: String, position: Option[FilePosition]): Unit = ()

  /** Clear any messages associated with file. */
  override def clear(file: File): Unit = ()

  /** Print message to log. */
  override def log(message: String): Unit = ()

  /** Start a subtask. */
  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = ()

  /** Show progress on a subtask. */
  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = ()

  /** Show completion of a subtask. */
  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit = ()
}
