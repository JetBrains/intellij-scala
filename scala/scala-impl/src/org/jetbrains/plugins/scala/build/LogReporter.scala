package org.jetbrains.plugins.scala.build
import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.openapi.diagnostic.Logger

import java.io.File

class LogReporter extends BuildReporter {

  private val logger = Logger.getInstance(classOf[LogReporter])

  /** Beginning of reporting. */
  override def start(): Unit = {
    logger.info("started task")
  }

  /** End of reporting. */
  override def finish(messages: BuildMessages): Unit = {
    logger.info(s"finished task with messages $messages")
  }

  /** Reporting ended due to error. */
  override def finishWithFailure(err: Throwable): Unit = {
    logger.error(s"finished with failure $err")
  }

  /** Reporting ends due to task being canceled. */
  override def finishCanceled(): Unit = {
    logger.info("finished with cancel")
  }

  /** Show warning message. */
  override def warning(message: String, position: Option[FilePosition]): Unit = {
    logger.warn(s"$message at $position")
  }

  /** Show error message. */
  override def error(message: String, position: Option[FilePosition]): Unit = {
    logger.error(s"$message at $position")
  }

  /** Show message. */
  override def info(message: String, position: Option[FilePosition]): Unit = {
    logger.info(s"$message at $position")
  }

  /** Clear any messages associated with file. */
  override def clear(file: File): Unit = {
    logger.debug(s"messages cleared for $file")
  }

  /** Print message to log. */
  override def log(message: String): Unit = {
    logger.info(message)
  }

  /** Start a subtask. */
  override def startTask(eventId: BuildMessages.EventId, parent: Option[BuildMessages.EventId], message: String, time: Long): Unit = {
    val parentStr = parent.map(p => s" ($p)").getOrElse("")
    logger.info(s"task started: $eventId$parentStr. $message ($time)")
  }

  /** Show progress on a subtask. */
  override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = {
    logger.debug(s"task progress: $eventId $progress/$total $unit. $message ($time)")
  }

  /** Show completion of a subtask. */
  override def finishTask(eventId: BuildMessages.EventId, message: String, result: EventResult, time: Long): Unit = {
    logger.info(s"task finished: $eventId. Result $result. $message ($time")
  }
}
