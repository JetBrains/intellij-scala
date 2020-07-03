package org.jetbrains.plugins.scala.build

import java.io.File

import com.intellij.build.events.MessageEvent.Kind
import com.intellij.build.events._
import com.intellij.build.{FilePosition, SyncViewManager}
import com.intellij.openapi.externalSystem.model.task.event.{FailureResult => _, SkippedResult => _, SuccessResult => _, _}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.ExternalSystemNotificationReporter._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Random

class ExternalSystemNotificationReporter(workingDir: String,
                                         taskId: ExternalSystemTaskId,
                                         notifications: ExternalSystemTaskNotificationListener)
  extends BuildReporter {

  private val descriptors: mutable.Map[EventId, TaskDescriptor] = mutable.Map.empty

  private val viewManager =
    Option(taskId.findProject()).map(_.getService(classOf[SyncViewManager]))

  override def start(): Unit = {
    notifications.onStart(taskId, workingDir)
  }

  override def finish(messages: BuildMessages): Unit = {
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
    notifications.onFailure(taskId, throwableToException(err))
    notifications.onEnd(taskId)
  }

  override def finishCanceled(): Unit = {
    notifications.onCancel(taskId)
    notifications.onEnd(taskId)
  }

  override def warning(message: String, position: Option[FilePosition]): Unit =
    viewManager
        .foreach(_.onEvent(taskId, event(message, Kind.WARNING, position)))

  override def error(message: String, position: Option[FilePosition]): Unit =
    viewManager.foreach(_.onEvent(taskId, event(message, Kind.ERROR, position)))

  override def info(message: String, position: Option[FilePosition]): Unit =
    viewManager.foreach(_.onEvent(taskId, event(message, Kind.INFO, position)))

  override def log(message: String): Unit =
    log(message, isStdOut = true)

  def logErr(message: String): Unit =
    log(message, isStdOut = false)

  private def log(message: String, isStdOut: Boolean): Unit = synchronized {
    notifications.onTaskOutput(taskId, message + "\n", isStdOut)
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

    descriptors.get(eventId).foreach { taskDescriptor =>
      val startTime = taskDescriptor.descriptor.getEventTime

      val resultObject = result match {
        case result: SuccessResult =>
          new SuccessResultImpl(startTime, time, true)
        case result: FailureResult =>
          val fails = result.getFailures.asScala.map(convertFailure).asJava
          new FailureResultImpl(startTime, time, fails)
        case result: SkippedResult =>
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

  private def event(@Nls message: String, kind: MessageEvent.Kind, position: Option[FilePosition])=
    BuildMessages.message(taskId, message, kind, position)
}

object ExternalSystemNotificationReporter {
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