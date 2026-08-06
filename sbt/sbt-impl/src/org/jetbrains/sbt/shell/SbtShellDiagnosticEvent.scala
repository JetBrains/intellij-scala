package org.jetbrains.sbt.shell

import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.shell.communication.SbtShellCommandRequestId
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState

/**
 * Structured diagnostic event emitted by [[SbtShellCommunication]].
 *
 * Some internal sbt shell operations are captured in a non-structured way as [[Trace]] events with arbitrary content.
 */
private[shell] sealed trait SbtShellDiagnosticEvent {
  def requestIdOpt: Option[SbtShellCommandRequestId] = None
}

private[shell] object SbtShellDiagnosticEvent {

  sealed trait CommandEvent extends SbtShellDiagnosticEvent {
    def requestId: SbtShellCommandRequestId
    override def requestIdOpt: Option[SbtShellCommandRequestId] = Some(requestId)
  }

  case class RunStart(requestId: SbtShellCommandRequestId, state: ShellState) extends CommandEvent
  case class RunFinish(requestId: SbtShellCommandRequestId, commandsSize: Int, afterRestartSize: Int, state: ShellState) extends CommandEvent
  case class EnqueueCommands(requestId: SbtShellCommandRequestId, shellWasReadyForImmediateSubmission: Boolean, state: ShellState) extends CommandEvent
  case class EnqueueAfterRestartCommands(requestId: SbtShellCommandRequestId, shellWasReadyForImmediateSubmission: Boolean, state: ShellState) extends CommandEvent
  case class TerminatePendingCommand(requestId: SbtShellCommandRequestId, state: ShellState) extends CommandEvent
  case class RemoveFromQueue(requestId: SbtShellCommandRequestId) extends CommandEvent
  case class CancelRequested(requestId: SbtShellCommandRequestId) extends CommandEvent
  case class ProcessCommandStart(requestId: SbtShellCommandRequestId, state: ShellState) extends CommandEvent
  case class ProcessCommandFinish(requestId: SbtShellCommandRequestId, result: String, state: ShellState) extends CommandEvent
  case class ErrorWaitForInputDetected(requestId: SbtShellCommandRequestId, state: ShellState) extends CommandEvent
  case class EnqueueCommandsAfterGateClosed(requestId: SbtShellCommandRequestId, shellWasReadyForImmediateSubmission: Boolean, state: ShellState) extends CommandEvent
  case class SendIgnore(sbtVersion: SbtVersion, isNewShell: Boolean, isLinux: Boolean, requiresNewLine: Boolean, command: String, state: ShellState) extends SbtShellDiagnosticEvent
  case class SendIgnoreSkipped(state: ShellState) extends SbtShellDiagnosticEvent
  case class Trace(message: String) extends SbtShellDiagnosticEvent

  def render(event: SbtShellDiagnosticEvent): String = event match {
    case RunStart(requestId, state) =>
      s"run start: requestId=$requestId, state=$state"
    case RunFinish(requestId, commandsSize, afterRestartSize, state) =>
      s"run finish: requestId=$requestId, commandsSize=$commandsSize, afterRestartSize=$afterRestartSize, state=$state"
    case EnqueueCommands(requestId, ready, state) =>
      s"enqueue commands: requestId=$requestId, shellWasReadyForImmediateSubmission=$ready, state=$state"
    case EnqueueAfterRestartCommands(requestId, ready, state) =>
      s"enqueue afterRestartCommands: requestId=$requestId, shellWasReadyForImmediateSubmission=$ready, state=$state"
    case TerminatePendingCommand(requestId, state) =>
      s"terminate pending command: requestId=$requestId, state=$state"
    case RemoveFromQueue(requestId) =>
      s"removeCommandFromQueueOrCancel removed from queue: requestId=$requestId"
    case CancelRequested(requestId) =>
      s"removeCommandFromQueueOrCancel not found, requesting cancellation: requestId=$requestId"
    case ProcessCommandStart(requestId, state) =>
      s"processCommand start: requestId=$requestId, state=$state"
    case ProcessCommandFinish(requestId, result, state) =>
      s"processCommand finish: requestId=$requestId, result=$result, state=$state"
    case ErrorWaitForInputDetected(requestId, state) =>
      s"ErrorWaitForInput detected: requestId=$requestId, state=$state"
    case EnqueueCommandsAfterGateClosed(requestId, ready, state) =>
      s"enqueue commands (gate closed): requestId=$requestId, shellWasReadyForImmediateSubmission=$ready, state=$state"
    case SendIgnore(sbtVersion, isNewShell, isLinux, requiresNewLine, command, state) =>
      s"sendIgnore: sbtVersion=$sbtVersion, isNewShell=$isNewShell, isLinux=$isLinux, requiresNewLine=$requiresNewLine, command=$command, state=$state"
    case SendIgnoreSkipped(state) =>
      s"sendIgnore skipped: state=$state"
    case Trace(message) =>
      message
  }
}
