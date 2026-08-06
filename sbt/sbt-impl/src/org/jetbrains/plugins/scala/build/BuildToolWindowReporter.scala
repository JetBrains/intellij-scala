package org.jetbrains.plugins.scala.build

import com.intellij.build.events.impl.*
import com.intellij.build.events.{BuildEvent, BuildEvents, EventResult, MessageEvent}
import com.intellij.build.issue.BuildIssue
import com.intellij.build.{AbstractViewManager, BuildContentManager, BuildViewManager, DefaultBuildDescriptor, FilePosition}
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator}
import com.intellij.openapi.project.{DumbAwareAction, Project}
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.sbt.SbtBundle

import java.nio.file.Path
import javax.swing.JComponent
import scala.concurrent.Promise

/**
 * Reports events to the Build Tool Window.
 *
 * @param activateToolWindowWhenFailed If true, activates the tool window when a finish event with failure result is emitted.
 * @param activateToolWindowWhenWarned If true, activates the tool window when the first warning is emitted.
 */
class BuildToolWindowReporter(
  project: Project,
  buildId: EventId,
  @Nls title: String,
  viewManager: AbstractViewManager,
  cancelAction: AnAction,
  activateToolWindowWhenFailed: Boolean,
  activateToolWindowWhenWarned: Boolean
) extends BuildReporter {

  import MessageEvent.Kind

  private var warningActivatedToolWindow = false

  def this(
    project: Project,
    buildId: EventId,
    @Nls title: String,
    cancelAction: AnAction,
    activateToolWindowWhenFailed: Boolean = true,
    activateToolWindowWhenWarned: Boolean = false
  ) =
    this(
      project,
      buildId,
      title,
      project.getService(classOf[BuildViewManager]),
      cancelAction,
      activateToolWindowWhenFailed = activateToolWindowWhenFailed,
      activateToolWindowWhenWarned = activateToolWindowWhenWarned
    )

  override def start(): Unit = {
    val buildDescriptor =
      new DefaultBuildDescriptor(buildId, title, project.getBasePath, System.currentTimeMillis())
        .withContentDescriptor { () => // dummy runContentDescriptor to set autofocus of build toolwindow off
          val descriptor = new RunContentDescriptor(null, null, new JComponent {}, title)
          descriptor.setActivateToolWindowWhenAdded(false)
          descriptor.setAutoFocusContent(false)
          descriptor
        }
        .withRestartActions(cancelAction)

    buildDescriptor.setActivateToolWindowWhenFailed(activateToolWindowWhenFailed)
    val startEvent =
      BuildEvents.getInstance()
        .startBuild(SbtBundle.message("report.build.toolwindow.running"), buildDescriptor)
        .build()
    viewManager.onEvent(buildId, startEvent)
  }

  override def finish(messages: BuildMessages): Unit = {
    @Nls
    val (result, resultMessage) =
      if (messages.status == BuildMessages.OK && messages.errors.isEmpty)
        (new SuccessResultImpl, "success")
      else if (messages.status == BuildMessages.Canceled)
        (new SkippedResultImpl, "canceled")
      else {
        (new FailureResultImpl(), "failed")
      }

    val finishEvent =
      BuildEvents.getInstance()
        .finishBuild(buildId, resultMessage, result)
        .withTime(System.currentTimeMillis())
        .build()
    viewManager.onEvent(buildId, finishEvent)
  }

  override def finishWithFailure(err: Throwable): Unit = {
    val finishEvent =
      BuildEvents.getInstance()
        .finishBuild(buildId, SbtBundle.message("report.build.toolwindow.failed"), new FailureResultImpl(err))
        .withTime(System.currentTimeMillis())
        .build()
    viewManager.onEvent(buildId, finishEvent)
  }

  override def finishCanceled(): Unit = {
    val finishEvent =
      BuildEvents.getInstance()
        .finishBuild(buildId, SbtBundle.message("report.build.toolwindow.canceled"), new SkippedResultImpl())
        .withTime(System.currentTimeMillis())
        .build()
    viewManager.onEvent(buildId, finishEvent)
  }

  override def startTask(taskId: EventId, parent: Option[EventId], message: String, time: Long = System.currentTimeMillis()): Unit = {
    val startEvent =
      BuildEvents.getInstance()
        .start(taskId, message)
        .withParentId(parent.orNull)
        .withTime(time)
        .build()
    viewManager.onEvent(buildId, startEvent)
  }

  override def progressTask(taskId: EventId, total: Long, progress: Long, unit: String, message: String, time: Long = System.currentTimeMillis()): Unit = {
    val event =
      BuildEvents.getInstance()
        .progress(taskId, message)
        .withTime(time)
        .withTotal(total)
        .withProgress(progress)
        .withUnit(if (unit == null) SbtBundle.message("report.build.toolwindow.items") else unit)
        .build()
    viewManager.onEvent(buildId, event)
  }

  override def finishTask(taskId: EventId, message: String, result: EventResult, time: Long = System.currentTimeMillis()): Unit = {
    val event = BuildEvents.getInstance()
      .finish(taskId, message, result)
      .withTime(time)
      .build()
    viewManager.onEvent(buildId, event)
  }

  override def clear(file: Path): Unit = ()

  override def warning(message: String, position: Option[FilePosition]): Unit = {
    activateToolWindowOnWarning()
    viewManager.onEvent(buildId, event(message, Kind.WARNING, position))
  }

  override def warning(message: String, position: Option[FilePosition], details: String): Unit = {
    activateToolWindowOnWarning()
    viewManager.onEvent(buildId, event(message, Kind.WARNING, position, details = details))
  }

  override def warning(issue: BuildIssue): Unit = {
    activateToolWindowOnWarning()
    val event = BuildEvents.getInstance()
      .buildIssue(issue, Kind.WARNING)
      .withParentId(buildId)
      .build()
    viewManager.onEvent(buildId, event)
  }

  override def warning(message: String, position: Option[FilePosition], details: String, navigatable: Option[Navigatable]): Unit = {
    activateToolWindowOnWarning()
    viewManager.onEvent(buildId, event(message, Kind.WARNING, position, details = details, navigatable = navigatable))
  }

  override def error(message: String, position: Option[FilePosition]): Unit =
    viewManager.onEvent(buildId, event(message, Kind.ERROR, position))

  override def info(message: String, position: Option[FilePosition]): Unit =
    viewManager.onEvent(buildId, event(message, Kind.INFO, position))

  override def log(message: String): Unit =
    viewManager.onEvent(buildId, logEvent(message, isStdout = true))

  override def logErr(message: String): Unit =
    viewManager.onEvent(buildId, logEvent(message, isStdout = false))

  private def logEvent(msg: String, isStdout: Boolean): BuildEvent = {
    val outputType =
      if (isStdout) ProcessOutputType.STDOUT
      else ProcessOutputType.STDERR
    BuildEvents.getInstance()
      .output(msg.trim + System.lineSeparator())
      .withParentId(buildId)
      .withOutputType(outputType)
      .build()
  }

  private def event(
    message: String,
    kind: MessageEvent.Kind,
    position: Option[FilePosition],
    details: String = null,
    navigatable: Option[Navigatable] = None
  ) = {
    //noinspection ReferencePassedToNls
    BuildMessages.message(buildId, message, kind, position, eventTime = System.currentTimeMillis, details, navigatable)
  }

  private def activateToolWindowOnWarning(): Unit =
    if (activateToolWindowWhenWarned && !warningActivatedToolWindow) {
      warningActivatedToolWindow = true
      ToolWindowManager.getInstance(project).invokeLater { () =>
        BuildContentManager.getInstance(project).getOrCreateToolWindow.activate(null, true)
      }
    }
}

object BuildToolWindowReporter {
  /** Action to cancel an ongoing build by failing the promise and canceling the progress indicator. */
  class CancelBuildAction(cancelToken: Promise[?], indicator: Option[ProgressIndicator])
    extends DumbAwareAction(SbtBundle.message("report.build.toolwindow.cancel"), SbtBundle.message("report.build.toolwindow.cancel"), AllIcons.Actions.Suspend) {

    override def actionPerformed(e: AnActionEvent): Unit = {
      cancelToken.failure(new ProcessCanceledException())
      indicator.foreach(_.cancel())
    }

    override def update(e: AnActionEvent): Unit =
      e.getPresentation.setEnabled(!cancelToken.isCompleted)

    override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
  }
}
