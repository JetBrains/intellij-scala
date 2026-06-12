package org.jetbrains.sbt.shell.communication

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.NonNls

/**
 * Describes one command submitted to the sbt shell.
 *
 * The command text is stored as a supplier because execution can be deferred until sbt shell initialization work completes.
 *
 * @param commandTextSupplier      lazily produces the sbt command text when the shell queue starts processing this request
 * @param requestId                identifies this request in the sbt shell command requests queue, logs, and cancellation path.
 * @param shellEventProcessor      processes shell events into the command result value.
 * @param interruptionErrorMessage optional error message used when the sbt shell terminates before the command finishes.
 * @param onQueuedWhileShellBusy   called when this request is queued while the sbt shell is not ready to accept it immediately.
 * @param activateSbtShellToolWindowOnStartup whether starting a fresh sbt shell for this request should activate the sbt shell tool window.
 * @param mirrorQueuedOutput       whether output printed while this request waits for a starting sbt shell should be routed to this request.
 * @tparam Result the command result type produced by shell-event processing.
 */
@Experimental
final case class SbtShellCommandRequest[Result] private(
  private val commandTextSupplier: () => String,
  requestId: SbtShellCommandRequestId,
  shellEventProcessor: SbtShellCommandEventProcessor[Result],
  interruptionErrorMessage: Option[String],
  onQueuedWhileShellBusy: () => Unit,
  activateSbtShellToolWindowOnStartup: Boolean,
  mirrorQueuedOutput: Boolean,
) {
  def sbtCommandText: String = commandTextSupplier()

  def withProcessorModified(f: SbtShellCommandEventProcessor[Result] => SbtShellCommandEventProcessor[Result]): SbtShellCommandRequest[Result] = {
    val newProcessor = f(shellEventProcessor)
    new SbtShellCommandRequest(
      commandTextSupplier,
      requestId,
      newProcessor,
      interruptionErrorMessage,
      onQueuedWhileShellBusy,
      activateSbtShellToolWindowOnStartup,
      mirrorQueuedOutput,
    )
  }

  def withQueuedWhileShellBusyNotification(onQueuedWhileShellBusy: () => Unit): SbtShellCommandRequest[Result] =
    new SbtShellCommandRequest(
      commandTextSupplier,
      requestId,
      shellEventProcessor,
      interruptionErrorMessage,
      onQueuedWhileShellBusy,
      activateSbtShellToolWindowOnStartup,
      mirrorQueuedOutput,
    )

  def withSbtShellToolWindowActivationOnStartup(enabled: Boolean): SbtShellCommandRequest[Result] =
    new SbtShellCommandRequest(
      commandTextSupplier,
      requestId,
      shellEventProcessor,
      interruptionErrorMessage,
      onQueuedWhileShellBusy,
      enabled,
      mirrorQueuedOutput,
    )

  def withQueuedOutputMirroring(): SbtShellCommandRequest[Result] =
    new SbtShellCommandRequest(
      commandTextSupplier,
      requestId,
      shellEventProcessor,
      interruptionErrorMessage,
      onQueuedWhileShellBusy,
      activateSbtShellToolWindowOnStartup,
      mirrorQueuedOutput = true,
    )
}

/**
 * Factory methods whose parameter semantics match the [[SbtShellCommandRequest]] constructor unless noted otherwise.
 */
@Experimental
object SbtShellCommandRequest {
  private def generatedRequestId: SbtShellCommandRequestId =
    SbtShellCommandRequestId.random()

  /**
   * Creates a request from by-name command text while keeping all other constructor options explicit.
   */
  private def create[Result](
    @NonNls sbtCommandText: => String,
    requestId: SbtShellCommandRequestId,
    shellEventProcessor: SbtShellCommandEventProcessor[Result],
    interruptionErrorMessage: Option[String]
  ): SbtShellCommandRequest[Result] =
    new SbtShellCommandRequest(
      () => sbtCommandText,
      requestId,
      shellEventProcessor,
      interruptionErrorMessage,
      () => (),
      activateSbtShellToolWindowOnStartup = true,
      mirrorQueuedOutput = false,
    )

  /**
   * Creates a request from by-name command text and a generated request id.
   */
  def apply[Result](
    @NonNls sbtCommandText: => String,
    shellEventProcessor: SbtShellCommandEventProcessor[Result],
    interruptionErrorMessage: Option[String] = None
  ): SbtShellCommandRequest[Result] =
    create(sbtCommandText, generatedRequestId, shellEventProcessor, interruptionErrorMessage)

  /**
   * Creates a generated-request-id request that collects shell output into a [[StringBuilder]].
   */
  def collectOutput(
    @NonNls sbtCommandText: => String
  ): SbtShellCommandRequest[StringBuilder] =
    collectOutput(sbtCommandText, generatedRequestId)

  /**
   * Creates a caller-provided-request-id request that collects shell output into a [[StringBuilder]].
   */
  def collectOutput(
    @NonNls sbtCommandText: => String,
    requestId: SbtShellCommandRequestId
  ): SbtShellCommandRequest[StringBuilder] =
    create(sbtCommandText, requestId, new SbtShellCommandEventProcessor.OutputCollector, None)
}
