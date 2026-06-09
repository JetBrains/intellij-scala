package org.jetbrains.sbt.shell.communication

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.{isInternalMode, isUnitTestMode}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

@Service(Array(Service.Level.PROJECT))
private[shell] final class SbtShellLifecycle {
  import SbtShellLifecycle.*

  private val transitionHistory: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]

  private lazy val useAdvancedStateLogging: Boolean =
    isInternalMode || isUnitTestMode

  def transition(state: ShellState, event: ShellStateEvent): ShellState = {
    val nextState = SbtShellLifecycle.transition(state, event)

    val nextOrCurrentState = nextState match {
      case Some(nextState) =>
        nextState
      case None =>
        logProhibitedTransition(state, event)
        state
    }
    saveTransitionIfNeeded(state, event, nextState)

    nextOrCurrentState
  }

  private def saveTransitionIfNeeded(
    state: ShellState,
    event: ShellStateEvent,
    nextState: Option[ShellState],
  ): Unit = {
    if (useAdvancedStateLogging) {
      val nextStateText = nextState.fold("None")(_.toString)
      transitionHistory.add(s"$state->$event->$nextStateText")
    }
  }

  private def logProhibitedTransition(
    state: ShellState,
    event: ShellStateEvent,
  ): Unit = {
    val msg = s"[SbtShellLifecycle] The prohibited $event event from $state. Ignored"
    if (useAdvancedStateLogging) {
      val history = transitionHistory.iterator.asScala.mkString("\n")
      val msgExtended =
        s"""$msg
           |Previous transitions:
           |${if (history.nonEmpty) history else "<empty>"}""".stripMargin.stripTrailing()
      log.error(msgExtended)
    } else {
      log.warn(msg)
    }
  }
}

private[shell] object SbtShellLifecycle {
  private val log = Logger.getInstance(getClass)

  def getInstance(project: Project): SbtShellLifecycle =
    project.getService(classOf[SbtShellLifecycle])

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

  /**
   * Calculates the next shell state for an allowed transition.
   *
   * @return Some(nextState) when the transition is allowed, None when the transition is prohibited.
   */
  private def transition(state: ShellState, event: ShellStateEvent): Option[ShellState] = {
    import ShellState.*
    import ShellStateEvent.*

    (state, event) match {
      case (Off, QueueDrained)            => Some(Idle)
      case (Off, EnqueueCommand)          => Some(Queued)
      case (Off, _)                       => None

      case (Idle, EnqueueCommand)         => Some(Queued)
      case (Idle, ShutdownRequested)      => Some(ShuttingDown)
      case (Idle, QueueDrained)           => Some(Idle) // The self-transition Idle -> Idle is allowed for now. It can happen when a ready prompt is observed while no command is queued or running.
      case (Idle, _)                      => None

      case (Queued, QueueDrained)           => Some(Idle)
      case (Queued, ShutdownRequested)      => Some(ShuttingDown)
      case (Queued, EnqueueCommand)         => Some(Queued)  // This occurs when the shell is in the Queued state and another command is added, triggering another EnqueueCommand event.
      // Another scenario is a ready prompt observed while command work is still queued or running.
      case (Queued, _)                      => None

      case (ShuttingDown, ProcessTerminated) => Some(Off)
      case (ShuttingDown, QueueDrained)      => Some(ShuttingDown) // QueueDrained & EnqueueCommand events may still be emitted after shutdown has started,
                                                                   // because SbtShellReadyLineListener#whenReady can fire even when the shell is already in the ShuttingDown state.
      case (ShuttingDown, EnqueueCommand)    => Some(ShuttingDown)
      case (ShuttingDown, _)                 => None
    }
  }
}
