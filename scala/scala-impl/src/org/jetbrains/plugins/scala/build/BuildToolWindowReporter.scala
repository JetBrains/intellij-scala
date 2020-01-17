package org.jetbrains.plugins.scala.build

import com.intellij.build.events.impl._
import com.intellij.build.events.{BuildEvent, EventResult, MessageEvent}
import com.intellij.build.{BuildViewManager, DefaultBuildDescriptor, FilePosition}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import org.jetbrains.plugins.scala.build.BuildMessages.EventId

class BuildToolWindowReporter(project: Project, buildId: EventId, title: String, viewManager: BuildViewManager)
  extends BuildTaskReporter {
  import MessageEvent.Kind

  def this(project: Project, buildId: EventId, title: String) =
    this(
      project, buildId, title,
      project.getService(classOf[BuildViewManager])
    )

  override def start(): Unit = {
    val buildDescriptor = new DefaultBuildDescriptor(buildId, title, project.getBasePath, System.currentTimeMillis())
    val startEvent = new StartBuildEventImpl(buildDescriptor, "running ...")
      .withContentDescriptorSupplier { () => // dummy runContentDescriptor to set autofocus of build toolwindow off
        val descriptor = new RunContentDescriptor(null, null, new JComponent {}, title)
        descriptor.setActivateToolWindowWhenAdded(false)
        descriptor.setAutoFocusContent(false)
        descriptor
      }

    viewManager.onEvent(buildId, startEvent)
  }

  override def finish(messages: BuildMessages): Unit = {
    val (result, resultMessage) =
      if (messages.status == BuildMessages.OK && messages.errors.isEmpty)
        (new SuccessResultImpl, "success")
      else if (messages.status == BuildMessages.Canceled)
        (new SkippedResultImpl, "canceled")
      else {
        (new FailureResultImpl(), "failed")
      }

    val finishEvent =
      new FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), resultMessage, result)
    viewManager.onEvent(buildId, finishEvent)
  }

  override def finishWithFailure(err: Throwable): Unit = {
    val failureResult = new FailureResultImpl(err)
    val finishEvent =
      new FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "failed", failureResult)
    viewManager.onEvent(buildId, finishEvent)
  }

  override def finishCanceled(): Unit = {
    val canceledResult = new SkippedResultImpl
    val finishEvent =
      new FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), "canceled", canceledResult)
    viewManager.onEvent(buildId, finishEvent)
  }

  override def startTask(taskId: EventId, parent: Option[EventId], message: String, time: Long = System.currentTimeMillis()): Unit = {
    val startEvent = new StartEventImpl(taskId, parent.orNull, time, message)
    viewManager.onEvent(buildId, startEvent)
  }

  override def progressTask(taskId: EventId, total: Long, progress: Long, unit: String, message: String, time: Long = System.currentTimeMillis()): Unit = {
    val time = System.currentTimeMillis() // TODO pass as parameter?
    val unitOrDefault = if (unit == null) "items" else unit
    val event = new ProgressBuildEventImpl(taskId, null, time, message, total, progress, unitOrDefault)
    viewManager.onEvent(buildId, event)
  }

  override def finishTask(taskId: EventId, message: String, result: EventResult, time: Long = System.currentTimeMillis()): Unit = {
    val time = System.currentTimeMillis() // TODO pass as parameter?
    val event = new FinishEventImpl(taskId, null, time, message, result)
    viewManager.onEvent(buildId, event)
  }

  override def warning(message: String, position: Option[FilePosition]): Unit =
    viewManager.onEvent(buildId, event(message, Kind.WARNING, position))

  override def error(message: String, position: Option[FilePosition]): Unit =
    viewManager.onEvent(buildId, event(message, Kind.ERROR, position))

  override def info(message: String, position: Option[FilePosition]): Unit =
    viewManager.onEvent(buildId, event(message, Kind.INFO, position))

  override def log(message: String): Unit =
    viewManager.onEvent(buildId, logEvent(message))

  private def logEvent(msg: String): BuildEvent =
    new OutputBuildEventImpl(buildId, msg.trim + System.lineSeparator(), true)

  private def event(message: String, kind: MessageEvent.Kind, position: Option[FilePosition])=
    BuildMessages.message(buildId, message, kind, position)

}
