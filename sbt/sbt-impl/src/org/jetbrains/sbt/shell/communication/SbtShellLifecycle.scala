package org.jetbrains.sbt.shell.communication

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.{isInternalMode, isUnitTestMode}

private[shell] object SbtShellLifecycle {
  private val log = Logger.getInstance(getClass)
  /**
   * Shell states
   *
   * @todo introduce more with SCL-24338 (most likely some "On" state and another one for emptying queue (before "soft restart"))
   */
  sealed trait ShellState
  object ShellState {
    /** The shell is alive, and no command is currently running or queued. */
    private[shell] case object Idle extends ShellState
    /**
     * The shell is alive and has commands pending in the standard command queue (see [[org.jetbrains.sbt.shell.SbtShellCommunication.commands]])
     * or the queue is empty but the last command is still running.
     */
    private[shell] case object Queued extends ShellState
    /** The shell is in the process of shutting down, but the process has not terminated yet. */
    private[shell] case object ShuttingDown extends ShellState
    /** The shell process is not running. */
    case object Off extends ShellState

    implicit class RichShellState(state: ShellState) {
      def isIdle: Boolean = state == ShellState.Idle
      def isQueued: Boolean = state == ShellState.Queued
      def isShuttingDown: Boolean = state == ShellState.ShuttingDown
      def isShuttingDownOrOff: Boolean = isShuttingDown || state == ShellState.Off
    }
  }

  // Events that trigger transition between states
  sealed trait ShellStateEvent
  object ShellStateEvent {
    case object EnqueueCommand extends ShellStateEvent
    case object QueueDrained extends ShellStateEvent
    case object ShutdownRequested extends ShellStateEvent
    case object ProcessTerminated extends ShellStateEvent
  }

  def transition(state: ShellState, event: ShellStateEvent): ShellState = {
    import ShellState.*
    import ShellStateEvent.*
    def logProhibitedTransition(): ShellState = {
      val msg = s"[SbtShellLifecycle] The prohibited $event event from $state. Ignored"
      if (isInternalMode || isUnitTestMode) log.error(msg)
      else log.warn(msg)

      state
    }

    (state, event) match {
      case (Off, QueueDrained)            => Idle
      case (Off, EnqueueCommand)          => Queued
      case (Off, _)                       => logProhibitedTransition()

      case (Idle, EnqueueCommand)         => Queued
      case (Idle, ShutdownRequested)      => ShuttingDown
      case (Idle, QueueDrained)           => Idle // The self-transition Idle -> Idle is allowed for now. It can happen when a ready prompt is observed while no command is queued or running.
      case (Idle, _)                      => logProhibitedTransition()

      case (Queued, QueueDrained)           => Idle
      case (Queued, ShutdownRequested)      => ShuttingDown
      case (Queued, EnqueueCommand)         => Queued  // This occurs when the shell is in the Queued state and another command is added, triggering another EnqueueCommand event.
      // Another scenario is a ready prompt observed while command work is still queued or running.
      case (Queued, _)                      => logProhibitedTransition()

      case (ShuttingDown, ProcessTerminated) => Off
      case (ShuttingDown, QueueDrained)      => ShuttingDown // QueueDrained & EnqueueCommand events may still be emitted after shutdown has started,
                                                             // because SbtShellReadyLineListener#whenReady can fire even when the shell is already in the ShuttingDown state.
      case (ShuttingDown, EnqueueCommand)    => ShuttingDown
      case (ShuttingDown, _)                 => logProhibitedTransition()
    }
  }
}
