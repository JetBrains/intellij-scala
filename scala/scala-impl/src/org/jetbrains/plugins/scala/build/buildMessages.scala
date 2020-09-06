package org.jetbrains.plugins.scala.build

import java.util.UUID
import java.util.function.BiPredicate

import com.intellij.build.events.impl.{AbstractBuildEvent, FileMessageEventImpl}
import com.intellij.build.events.{MessageEvent, MessageEventResult, Warning}
import com.intellij.build.{FilePosition, events}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.task._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.build.BuildMessages.{BuildStatus, Canceled, Error}

case class BuildMessages(warnings: Seq[events.Warning],
                         errors: Seq[events.Failure],
                         exceptions: Seq[Exception],
                         messages: Seq[String],
                         status: BuildStatus) {
  def addError(msg: String): BuildMessages = copy(errors = errors :+ BuildFailure(msg.trim))
  def addWarning(msg: String): BuildMessages = copy(warnings = warnings :+ BuildWarning(msg.trim))
  def status(buildStatus: BuildStatus): BuildMessages = copy(status = buildStatus)
  def exception(exception: Exception): BuildMessages = copy(exceptions = exceptions :+ exception, status = Error)
  def message(msg: String): BuildMessages = copy(messages = messages :+ msg)
  def combine(other: BuildMessages): BuildMessages = BuildMessages(
    this.warnings ++ other.warnings,
    this.errors ++ other.errors,
    this.exceptions ++ other.exceptions,
    this.messages ++ other.messages,
    this.status.combine(other.status)
  )

  def toTaskRunnerResult: ProjectTaskRunner.Result = {
    TaskRunnerResult(
      status == Canceled,
      status == Error || errors.nonEmpty
    )
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

  def empty: BuildMessages = BuildMessages(Vector.empty, Vector.empty, Vector.empty, Vector.empty, BuildMessages.Indeterminate)

  def message(parentId: Any, @Nls message: String, kind: MessageEvent.Kind, position: Option[FilePosition]): AbstractBuildEvent with MessageEvent = {
    val kindGroup = kind.toString

    position match {
      case None => new BuildEventMessage(parentId, kind, kindGroup, message)
      case Some(filePosition) => new FileMessageEventImpl(parentId, kind, kindGroup, message, null, filePosition)
    }
  }
}

class BuildEventMessage(parentId: Any, kind: MessageEvent.Kind, @Nls group: String, @Nls message: String)
  extends AbstractBuildEvent(new Object, parentId, System.currentTimeMillis(), message) with MessageEvent {

  override def getKind: MessageEvent.Kind = kind

  override def getGroup: String = group

  override def getResult: MessageEventResult =
    new MessageEventResult() {
      override def getKind: MessageEvent.Kind = kind
    }

  override def getNavigatable(project: Project): Navigatable = null // TODO sensible default navigation?
}

case class TaskRunnerResult(override val isAborted: Boolean,
                            override val hasErrors: Boolean) extends ProjectTaskRunner.Result

case class TaskManagerResult(context: ProjectTaskContext,
                             override val isAborted: Boolean,
                             override val hasErrors: Boolean) extends ProjectTaskManager.Result {

  override def getContext: ProjectTaskContext = context

  override def anyTaskMatches(predicate: BiPredicate[_ >: ProjectTask, _ >: ProjectTaskState]): Boolean =
    false // TODO figure out what this is supposed to do?
}

case class BuildFailure(@Nls message: String) extends events.impl.FailureImpl(message, /*description*/ null: String)

case class BuildWarning(@Nls message: String) extends Warning {
  override def getMessage: String = message
  override def getDescription: String = null
}
