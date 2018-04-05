package org.jetbrains.plugins.scala.build

import java.util
import java.util.UUID

import com.intellij.build.events.impl._
import com.intellij.build.events.{BuildEvent, MessageEvent, MessageEventResult, Warning}
import com.intellij.build.{BuildViewManager, DefaultBuildDescriptor, events}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.task.ProjectTaskResult
import javax.swing.JComponent

import scala.collection.JavaConverters._

trait BuildReporter {
  def start()
  def finish(messages: BuildMessages): Unit
  def finishWithFailure(err: Throwable)
  def warning(message: String): Unit
  def error(message: String): Unit
  def output(message: String): Unit
}

class IndicatorReporter(indicator: ProgressIndicator) extends BuildReporter {
  override def start(): Unit = {
    indicator.setText("build queued ...")
  }

  override def finish(messages: BuildMessages): Unit = {
    indicator.setText2("")

    if (messages.errors.isEmpty)
      indicator.setText("build completed")
    else
      indicator.setText("build failed")
  }

  override def finishWithFailure(err: Throwable): Unit = {
    indicator.setText(s"errored: ${err.getMessage}")
  }

  override def warning(message: String): Unit = {}

  override def error(message: String): Unit = {}

  override def output(message: String): Unit = {
    indicator.setText("building ...")
    indicator.setText2(message)
  }
}

class BuildToolWindowReporter(project: Project, taskId: UUID, title: String) extends BuildReporter {

  private lazy val viewManager: BuildViewManager = ServiceManager.getService(project, classOf[BuildViewManager])

  // TODO these should be configurable by build system
  private val outputEvent: String => BuildEvent =
    msg => new OutputBuildEventImpl(taskId, msg.trim + System.lineSeparator(), true)
  private val warnEvent: String => MessageEvent = BuildWarningMessage(taskId, _)
  private val errorEvent: String => MessageEvent = BuildErrorMessage(taskId, _)

  def start(): Unit = {
    val buildDescriptor = new DefaultBuildDescriptor(taskId, title, project.getBasePath, System.currentTimeMillis())
    val startEvent = new StartBuildEventImpl(buildDescriptor, "queued ...")
      .withContentDescriptorSupplier { () => // dummy runContentDescriptor to set autofocus of build toolwindow off
        val descriptor = new RunContentDescriptor(null, null, new JComponent {}, title)
        descriptor.setActivateToolWindowWhenAdded(false)
        descriptor.setAutoFocusContent(false)
        descriptor
      }

    viewManager.onEvent(startEvent)
  }

  def finish(messages: BuildMessages): Unit = {
    val (result, resultMessage) =
      if (messages.errors.isEmpty)
        (new SuccessResultImpl, "success")
      else {
        val fails: util.List[events.Failure] = messages.errors.asJava
        (new FailureResultImpl(fails), "failed")
      }

    val finishEvent =
      new FinishBuildEventImpl(taskId, null, System.currentTimeMillis(), resultMessage, result)
    viewManager.onEvent(finishEvent)
  }

  def finishWithFailure(err: Throwable): Unit = {
    val failureResult = new FailureResultImpl(err)
    val finishEvent =
      new FinishBuildEventImpl(taskId, null, System.currentTimeMillis(), "failed", failureResult)
    viewManager.onEvent(finishEvent)
  }

  def warning(message: String): Unit =
    viewManager.onEvent(warnEvent(message))

  def error(message: String): Unit =
    viewManager.onEvent(errorEvent(message))

  def output(message: String): Unit = {
    viewManager.onEvent(outputEvent(message))
  }

}

abstract class BuildEventMessage(parentId: Any, kind: MessageEvent.Kind, group: String, message: String)
  extends AbstractBuildEvent(new Object, parentId, System.currentTimeMillis(), message) with MessageEvent {

  override def getKind: MessageEvent.Kind = kind
  override def getGroup: String = group

  override def getResult: MessageEventResult =
    new MessageEventResult() {
      override def getKind: MessageEvent.Kind = kind
    }

  override def getNavigatable(project: Project): Navigatable = null // TODO sensible default navigation?
}

case class BuildWarningMessage(parentId: Any, message: String)
  extends BuildEventMessage(parentId, MessageEvent.Kind.WARNING, "warnings", message)

case class BuildErrorMessage(parentId: Any, message: String)
  extends BuildEventMessage(parentId, MessageEvent.Kind.ERROR, "errors", message)


case class BuildMessages(warnings: Seq[events.Warning], errors: Seq[events.Failure], log: Seq[String], aborted: Boolean) {
  def appendMessage(text: String): BuildMessages = copy(log = log :+ text.trim)
  def addError(msg: String): BuildMessages = copy(errors = errors :+ BuildFailure(msg.trim))
  def addWarning(msg: String): BuildMessages = copy(warnings = warnings :+ BuildWarning(msg.trim))
  def abort: BuildMessages = copy(aborted = true)
  def toTaskResult: ProjectTaskResult = new ProjectTaskResult(aborted, errors.size, warnings.size)
}

case object BuildMessages {
  def empty = BuildMessages(Vector.empty, Vector.empty, Vector.empty, aborted = false)
}

case class BuildFailure(message: String) extends events.impl.FailureImpl(message, /*description*/ null: String)

case class BuildWarning(message: String) extends Warning {
  override def getMessage: String = message
  override def getDescription: String = null
}

case class BuildFailureException(msg: String) extends Exception(msg)