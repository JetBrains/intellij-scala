package org.jetbrains.plugins.scala.build

import com.intellij.build.events.MessageEvent.Kind
import com.intellij.build.events._
import com.intellij.build.{FilePosition, SyncViewManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.event.{FailureResult => _, SkippedResult => _, SuccessResult => _, _}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.ExternalSystemNotificationReporter._

import java.io.File
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Random

class ExternalSystemNotificationReporter(workingDir: String,
                                         taskId: ExternalSystemTaskId,
                                         notifications: ExternalSystemTaskNotificationListener)
  extends BuildReporter {

  private val descriptors: mutable.Map[EventId, TaskDescriptor] = mutable.Map.empty

  private val viewManager: Option[SyncViewManager] =
    Option(taskId.findProject()).map(_.getService(classOf[SyncViewManager]))

  private var isFinished: Boolean = false

  override def start(): Unit = {
    notifications.onStart(taskId, workingDir)
  }

  override def finish(messages: BuildMessages): Unit = {
    isFinished = true
    if (messages.status == BuildMessages.OK && messages.errors.isEmpty) {
      notifications.onEnd(taskId)
      notifications.onSuccess(taskId)
    }
    else if (messages.status == BuildMessages.Canceled) {
      finishCanceled()
    }
    else if (messages.exceptions.nonEmpty) {
      finishWithFailure(messages.exceptions.head)
    } else if (messages.errors.nonEmpty) {
      val firstError = messages.errors.head
      val throwable = Option(firstError.getError).getOrElse(new Exception(firstError.getMessage))
      finishWithFailure(throwable)
    } else if (messages.status == BuildMessages.Indeterminate) {
      finishWithFailure(new Exception("task ended in indeterminate state"))
    } else {
      finishWithFailure(new Exception("task failed"))
    }
  }

  override def finishWithFailure(err: Throwable): Unit = {
    isFinished = true
    notifications.onFailure(taskId, throwableToException(err))
    notifications.onEnd(taskId)
  }

  override def finishCanceled(): Unit = {
    isFinished = true
    val time = System.currentTimeMillis()
    descriptors.foreach { case (id, _) =>
      val result = new com.intellij.build.events.impl.SkippedResultImpl
      finishTask(id, CompilerSharedBuildBundle.message("report.build.task.canceled"), result, time)
    }
    notifications.onCancel(taskId)
  }

  override def warning(message: String, position: Option[FilePosition]): Unit =
    onEvent(message, Kind.WARNING, position)

  override def warning(message: String, position: Option[FilePosition], details: String): Unit =
    onEvent(message, Kind.WARNING, position, details)

  override def error(message: String, position: Option[FilePosition]): Unit =
    onEvent(message, Kind.ERROR, position)

  override def info(message: String, position: Option[FilePosition]): Unit =
    onEvent(message, Kind.INFO, position)

  private def onEvent(@Nls message: String, kind: Kind, position: Option[FilePosition], @Nls details: String = null): Unit = {
    viewManager.foreach(_.onEvent(taskId, event(message, kind, position, details)))
  }

  override def log(message: String): Unit =
    log(message, isStdOut = true)

  def logErr(message: String): Unit =
    log(message, isStdOut = false)

  private def log(message: String, isStdOut: Boolean): Unit = synchronized {
    if (!isFinished) {
      //NOTE: new lines are also added in BspSession.BspProcessMessageHandler.call
      val messageWithNewLine = if (message.endsWith("\n")) message else message + "\n"
      notifications.onTaskOutput(taskId, messageWithNewLine, isStdOut)
    } else {
      //NOTE: it might be not valid to log output when the task is already finished
      //(see comments of https://youtrack.jetbrains.com/issue/SCL-21794/Reload-ALL-BSP-projects-action-is-disabled)
      Log.warn(s"Skipping output for a finished task (taskId: $taskId, stdout: $isStdOut): ${message.trim}")
    }
  }

  override def startTask(eventId: EventId, parent: Option[EventId], message: String, time: Long = System.currentTimeMillis()): Unit = {
    val taskDescriptor = descriptors.getOrElseUpdate(
      eventId,
      TaskDescriptor(
        new TaskOperationDescriptorImpl(message, System.currentTimeMillis(), "task-" + Random.nextLong()),
        parent
      )
    )

    val startEvent =
      new ExternalSystemStartEventImpl[TaskOperationDescriptor](eventId.id, parent.map(_.id).orNull, taskDescriptor.descriptor)
    val statusEvent =
      new ExternalSystemTaskExecutionEvent(taskId, startEvent)
    notifications.onStatusChange(statusEvent)
  }

  override def progressTask(eventId: EventId, total: Long, progress: Long, unit: String, message: String, time: Long = System.currentTimeMillis()): Unit = {
    descriptors.get(eventId).foreach { taskDescriptor =>
      val progressEvent = new ExternalSystemStatusEventImpl[TaskOperationDescriptor](
        eventId.id, taskDescriptor.parent.map(_.id).orNull, taskDescriptor.descriptor, total, progress, unit
      )
      val statusEvent = new ExternalSystemTaskExecutionEvent(taskId, progressEvent)
      notifications.onStatusChange(statusEvent)
    }
  }

  override def finishTask(eventId: EventId, message: String, result: EventResult, time: Long = System.currentTimeMillis()): Unit = {
    descriptors
      .remove(eventId)
      .foreach { taskDescriptor =>
        val startTime = taskDescriptor.descriptor.getEventTime

        val resultObject = result match {
          case _: SuccessResult =>
            new SuccessResultImpl(startTime, time, true)
          case result: FailureResult =>
            val fails = result.getFailures.asScala.map(convertFailure).asJava
            new FailureResultImpl(startTime, time, fails)
          case _: SkippedResult =>
            new SkippedResultImpl(startTime, time)
          case _ => // unknown or unhandled result type
            new SkippedResultImpl(startTime, time)
        }

        val finishEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](
          eventId.id, taskDescriptor.parent.map(_.id).orNull, taskDescriptor.descriptor, resultObject)
        val statusEvent = new ExternalSystemTaskExecutionEvent(taskId, finishEvent)
        notifications.onStatusChange(statusEvent)
      }
  }

  override def clear(file: File): Unit = ()

  private def event(@Nls message: String, kind: MessageEvent.Kind, position: Option[FilePosition], @Nls @Nullable details: String)=
    BuildMessages.message(taskId, message, kind, position, eventTime = System.currentTimeMillis, details)
}

object ExternalSystemNotificationReporter {
  private val Log = Logger.getInstance(this.getClass)

  private case class TaskDescriptor(descriptor: TaskOperationDescriptor, parent: Option[EventId])

  private def throwableToException(throwable: Throwable) = throwable match {
    case x: Exception => x
    case t: Throwable => new Exception(t)
  }

  private def convertFailure(fail: com.intellij.build.events.Failure):
  com.intellij.openapi.externalSystem.model.task.event.Failure = {
    val causes = fail.getCauses.asScala.map(convertFailure).asJava
    new FailureImpl(fail.getMessage, fail.getDescription, causes)
  }
}