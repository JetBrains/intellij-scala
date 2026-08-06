package org.jetbrains.sbt.shell.communication

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.{isInternalMode, isUnitTestMode}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

@Service(Array(Service.Level.PROJECT))
private[shell] final class SbtShellLifecycle {
  import SbtShellLifecycle.*

  private val transitionHistory: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]

  private lazy val useAdvancedStateLogging: Boolean =
    isInternalMode || isUnitTestMode

  @TestOnly
  private[shell] def transitionHistorySnapshot: Seq[String] =
    transitionHistory.iterator.asScala.toSeq

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
   */
  sealed trait ShellState
  object ShellState {
    /**
     * The shell process has been acquired and is starting up, but no ready prompt has been observed yet.
     */
    private[shell] case object Starting extends ShellState
    /** The shell is alive, and no command is currently running or queued. */
    private[shell] case object Idle extends ShellState
    /**
     * The shell is alive and has commands pending in the standard command queue (see [[org.jetbrains.sbt.shell.SbtShellCommunication.commands]])
     * or the queue is empty but the last command is still running.
     */
    private[shell] case object Queued extends ShellState
    /**
     * A soft restart has been initiated. Commands from the standard queue may continue to be processed.
     * New commands arriving during this state are buffered in [[AfterRestartCommandsGate]] for execution after the shell restarts.
     */
    private[shell] case object SoftRestarting extends ShellState
    /** The shell is in the process of shutting down, but the process has not terminated yet. */
    private[shell] case object ShuttingDown extends ShellState
    /** The shell process is not running. */
    case object Off extends ShellState

    implicit class RichShellState(state: ShellState) {
      def isStarting: Boolean = state == ShellState.Starting
      def isIdle: Boolean = state == ShellState.Idle
      def isQueued: Boolean = state == ShellState.Queued
      def isSoftRestarting: Boolean = state == ShellState.SoftRestarting
      def isShuttingDown: Boolean = state == ShellState.ShuttingDown
      def isOff: Boolean = state == ShellState.Off
      def isShuttingDownOrOff: Boolean = isShuttingDown || isOff
    }
  }

  // Events that trigger transition between states
  sealed trait ShellStateEvent
  object ShellStateEvent {
    case object EnqueueCommand extends ShellStateEvent
    /** Communication with a freshly acquired sbt process has begun. */
    case object StartupInitiated extends ShellStateEvent
    /**
     * The shell reported a ready prompt.
     *
     * @param queuePending whether the standard command queue was non-empty when the prompt was observed.
     */
    case class ReadyPromptObserved(queuePending: Boolean) extends ShellStateEvent
    /**
     * The shell printed non-prompt output after it had already reported a ready prompt.
     *
     * This is needed for commands that do not go through [[org.jetbrains.sbt.shell.SbtShellCommunication.run]],
     * for example commands typed manually in the sbt shell tool window. Such commands do not enqueue an IDEA-side
     * request, so [[EnqueueCommand]] would incorrectly describe the cause of the busy state and would not protect
     * against submitting a queued run configuration while the manually entered command is still running.
     *
     * The [[ReadyPromptObserved]] transition is also not enough: the communication layer can be in
     * [[ShellState.Idle]] after the previous prompt, while sbt has already accepted a manual command and started
     * producing output. This event records that observed work and keeps later IDEA requests waiting for the next
     * ready prompt.
     */
    case object ShellBecameBusy extends ShellStateEvent
    case object SoftRestartInitiated extends ShellStateEvent
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

    def readyState(queuePending: Boolean): ShellState =
      if queuePending then Queued else Idle

    (state, event) match {
      case (Off, StartupInitiated)             => Some(Starting)
      case (Off, EnqueueCommand)               => Some(Off)   // A command may be queued before the process is up.
      case (Off, _)                            => None

      case (Starting, ReadyPromptObserved(queuePending)) => Some(readyState(queuePending))
      case (Starting, EnqueueCommand)    => Some(Starting)
      case (Starting, ShutdownRequested) => Some(ShuttingDown)
      case (Starting, _)                 => None

      case (Idle, EnqueueCommand)       => Some(Queued)
      case (Idle, ShellBecameBusy)      => Some(Queued)
      case (Idle, ReadyPromptObserved(queuePending)) => Some(readyState(queuePending)) // The self-transition Idle -> Idle is allowed for now. It can happen when a ready prompt is observed while no command is queued or running.
      case (Idle, SoftRestartInitiated) => Some(SoftRestarting)
      case (Idle, ShutdownRequested)    => Some(ShuttingDown)
      case (Idle, _)                    => None

      case (Queued, SoftRestartInitiated) => Some(SoftRestarting)
      case (Queued, ShutdownRequested)    => Some(ShuttingDown)
      case (Queued, EnqueueCommand)       => Some(Queued)  // This occurs when the shell is in the Queued state and another command is added, triggering another EnqueueCommand event.
      case (Queued, ShellBecameBusy)      => Some(Queued)
      case (Queued, ReadyPromptObserved(queuePending)) => Some(readyState(queuePending))
      case (Queued, _)                    => None

      case (SoftRestarting, ShutdownRequested)      => Some(ShuttingDown) // Hard kill overrides soft restart, or soft restart enters process destruction phase
      case (SoftRestarting, ProcessTerminated)      => Some(Off)
      case (SoftRestarting, ReadyPromptObserved(_)) => Some(SoftRestarting)
      case (SoftRestarting, EnqueueCommand)         => Some(SoftRestarting) // New commands may be submitted during soft restart and are routed to the after-restart buffer (same as ShuttingDown state).
      case (SoftRestarting, ShellBecameBusy)        => Some(SoftRestarting)
      case (SoftRestarting, _)                      => None

      case (ShuttingDown, ProcessTerminated)      => Some(Off)
      case (ShuttingDown, ReadyPromptObserved(_)) => Some(ShuttingDown) // ReadyPromptObserved, EnqueueCommand & ShellBecameBusy events may still be emitted after shutdown has started,
                                                                        // because SbtShellReadyLineListener#whenReady can fire even when the shell is already in the ShuttingDown state.
      case (ShuttingDown, EnqueueCommand)         => Some(ShuttingDown)
      case (ShuttingDown, ShellBecameBusy)        => Some(ShuttingDown)
      case (ShuttingDown, _)                      => None
    }
  }
}
