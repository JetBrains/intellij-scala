package org.jetbrains.plugins.scala.build

import com.intellij.build.events.{BuildEvent, BuildEvents, MessageEvent, Warning}
import com.intellij.build.{FilePosition, events}
import com.intellij.execution.process.{AnsiEscapeDecoder, ProcessOutputTypes}
import com.intellij.pom.Navigatable
import com.intellij.task._
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.build.BuildMessages.{BuildStatus, Canceled, Error}

import java.util.UUID
import java.util.function.BiPredicate

case class BuildMessages(
  warnings: Seq[events.Warning],
  errors: Seq[events.Failure],
  exceptions: Seq[Exception],
  messages: Seq[String],
  status: BuildStatus
) {
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
    def combine(other: BuildStatus): BuildStatus = (this, other) match {
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

  /**
   * Strips ANSI escape codes from the given message.
   *
   * ATTENTION! 
   * The DECKPNM ANSI escape sequence (ESC >) is not currently handled by AnsiEscapeDecoder.
   * This sequence is emitted when the "new" shell is running (using "shell" command instead of "idea-shell").
   * Remove this workaround once the issue is fixed on the platform side (see IJPL-210647).
   *
   * @param stripDeckpnm whether to escape DECKPNM (Keypad Numeric Mode) from the message
   */
  def stripAnsiCodes(@Nullable message: String, stripDeckpnm: Boolean = false): String =
    if (message == null) null
    else {
      val builder = new StringBuilder()
      new AnsiEscapeDecoder().escapeText(message, ProcessOutputTypes.STDOUT, (text, _) => builder.append(text))
      val result = builder.result()
      if (stripDeckpnm) {
        result.replace("\u001b>", "")
      } else {
        result
      }
    }

  def message(
    parentId: Any,
    @Nls message: String,
    kind: MessageEvent.Kind,
    position: Option[FilePosition],
    eventTime: Long,
    @Nls @Nullable details: String = null,
    navigatable: Option[Navigatable] = None,
  ): BuildEvent = {
    val kindGroup = kind.toString

    position match {
      case None =>
        BuildEvents.getInstance().message()
          .withParentId(parentId)
          .withKind(kind)
          .withTime(eventTime)
          .withGroup(kindGroup)
          .withMessage(stripAnsiCodes(message))
          .withDescription(details)
          .withNavigatable(navigatable.orNull)
          .build()
      case Some(filePosition) =>
        BuildEvents.getInstance().fileMessage()
          .withParentId(parentId)
          .withKind(kind)
          .withTime(eventTime)
          .withGroup(kindGroup)
          .withMessage(stripAnsiCodes(message))
          .withDescription(details)
          .withFilePosition(filePosition)
          .build()
    }
  }
}

case class TaskRunnerResult(
  override val isAborted: Boolean,
  override val hasErrors: Boolean
) extends ProjectTaskRunner.Result

case class TaskManagerResult(
  context: ProjectTaskContext,
  override val isAborted: Boolean,
  override val hasErrors: Boolean
) extends ProjectTaskManager.Result {

  override def getContext: ProjectTaskContext = context

  override def anyTaskMatches(predicate: BiPredicate[? >: ProjectTask, ? >: ProjectTaskState]): Boolean =
    false // TODO figure out what this is supposed to do?
}

case class BuildFailure(@Nls message: String) extends events.impl.FailureImpl(message, /*description*/ null: String)

case class BuildWarning(@Nls message: String) extends Warning {
  override def getMessage: String = message

  override def getDescription: String = null
}