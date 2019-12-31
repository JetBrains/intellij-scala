package org.jetbrains.plugins.scala.build

import java.util.UUID

import com.intellij.build.{FilePosition, events}
import com.intellij.build.events.impl.{AbstractBuildEvent, FileMessageEventImpl}
import com.intellij.build.events.{MessageEvent, MessageEventResult}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.task.{ProjectTaskContext, ProjectTaskManager, ProjectTaskResult}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.build.BuildMessages.{BuildStatus, Canceled, Error}

case class BuildMessages(warnings: Seq[events.Warning], errors: Seq[events.Failure], exceptions: Seq[Exception], status: BuildStatus) {
  def addError(msg: String): BuildMessages = copy(errors = errors :+ BuildFailure(msg.trim))
  def addWarning(msg: String): BuildMessages = copy(warnings = warnings :+ BuildWarning(msg.trim))
  def status(buildStatus: BuildStatus): BuildMessages = copy(status = buildStatus)
  def exception(exception: Exception): BuildMessages = copy(exceptions = exceptions :+ exception, status = Error)
  def combine(other: BuildMessages): BuildMessages = BuildMessages(
    this.warnings ++ other.warnings,
    this.errors ++ other.errors,
    this.exceptions ++ other.exceptions,
    this.status.combine(other.status)
  )

  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @deprecated
  def toTaskResult =
    new ProjectTaskResult(
      status == Canceled || status == Error,
      errors.size,
      warnings.size
    )

  def toTaskManagerResult: ProjectTaskManager.Result = {
    val context = new ProjectTaskContext() // TODO figure out what goes in here
    val isAborted = status == Canceled || status == Error
    TaskManagerResult(context, isAborted, errors.nonEmpty)
  }
}

case object BuildMessages {

  sealed abstract class BuildStatus {
    def combine(other: BuildStatus): BuildStatus = (this,other) match {
      case (Indeterminate, s) => s
      case (s, Indeterminate) => s
      case (Error, _) | (_, Error) => Error
      case (Canceled, _) | (_, Canceled) => Canceled
      case (OK, OK) => OK
    }
  }

  case object Indeterminate extends BuildStatus
  case object OK extends BuildStatus
  case object Error extends BuildStatus
  case object Canceled extends BuildStatus


  case class EventId(id: String) extends AnyVal

  def randomEventId: EventId = EventId(UUID.randomUUID().toString)

  def empty: BuildMessages = BuildMessages(Vector.empty, Vector.empty, Vector.empty, BuildMessages.Indeterminate)

  def message(parentId: Any, message: String, kind: MessageEvent.Kind, position: Option[FilePosition]): AbstractBuildEvent with MessageEvent =
    position match {
      case None => new BuildEventMessage(parentId, kind, kind.toString, message)
      case Some(filePosition) => new FileMessageEventImpl(parentId, kind, kind.toString, message, null, filePosition)
    }
}

class BuildEventMessage(parentId: Any, kind: MessageEvent.Kind, group: String, message: String)
  extends AbstractBuildEvent(new Object, parentId, System.currentTimeMillis(), message) with MessageEvent {

  override def getKind: MessageEvent.Kind = kind

  override def getGroup: String = group

  override def getResult: MessageEventResult =
    new MessageEventResult() {
      override def getKind: MessageEvent.Kind = kind
    }

  override def getNavigatable(project: Project): Navigatable = null // TODO sensible default navigation?
}