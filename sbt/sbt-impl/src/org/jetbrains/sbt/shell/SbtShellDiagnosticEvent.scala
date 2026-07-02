package org.jetbrains.sbt.shell

import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.shell.communication.SbtShellCommandRequestId
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.ShellState

/**
 * Structured diagnostic event emitted by [[SbtShellCommunication]].
 *
 * Some internal sbt shell operations are captured in a non-structured way as [[Trace]] events with arbitrary content.
 */
private[shell] enum SbtShellDiagnosticEvent {
  case RunStart(requestId: SbtShellCommandRequestId, state: ShellState)
  case RunFinish(requestId: SbtShellCommandRequestId, commandsSize: Int, afterRestartSize: Int, state: ShellState)
  case EnqueueCommands(requestId: SbtShellCommandRequestId, shellWasReadyForImmediateSubmission: Boolean, state: ShellState)
  case EnqueueAfterRestartCommands(requestId: SbtShellCommandRequestId, shellWasReadyForImmediateSubmission: Boolean, state: ShellState)
  case TerminatePendingCommand(requestId: SbtShellCommandRequestId, state: ShellState)
  case RemoveFromQueue(requestId: SbtShellCommandRequestId)
  case CancelRequested(requestId: SbtShellCommandRequestId)
  case ProcessCommandStart(requestId: SbtShellCommandRequestId, state: ShellState)
  case ProcessCommandFinish(requestId: SbtShellCommandRequestId, result: String, state: ShellState)
  case ErrorWaitForInputDetected(requestId: SbtShellCommandRequestId, state: ShellState)
  case SendIgnore(sbtVersion: SbtVersion, isNewShell: Boolean, isLinux: Boolean, requiresNewLine: Boolean, command: String, state: ShellState)
  case SendIgnoreSkipped(state: ShellState)
  case Trace(message: String)
}

private[shell] object SbtShellDiagnosticEvent {
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
    case SendIgnore(sbtVersion, isNewShell, isLinux, requiresNewLine, command, state) =>
      s"sendIgnore: sbtVersion=$sbtVersion, isNewShell=$isNewShell, isLinux=$isLinux, requiresNewLine=$requiresNewLine, command=$command, state=$state"
    case SendIgnoreSkipped(state) =>
      s"sendIgnore skipped: state=$state"
    case Trace(message) =>
      message
  }
}
