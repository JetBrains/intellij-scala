package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.{ApiStatus, TestOnly, VisibleForTesting}
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.{isInternalMode, isUnitTestMode}
import org.jetbrains.sbt.shell.SbtShellCommunication.*
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.{ShellState, ShellStateEvent}
import org.jetbrains.sbt.shell.communication.ShellEvent.ErrorWaitForInput
import org.jetbrains.sbt.shell.communication.SbtShellQueuedStartupOutputMirroring.Owner
import org.jetbrains.sbt.shell.communication.{SbtOutputCompleteLinesProcessListener, SbtShellCommandExecutionOutputListener, SbtShellCommandRequest, SbtShellCommandRequestId, SbtShellCommandSubmitter, SbtShellLifecycle, SbtShellOutputRecognizer, SbtShellQueuedStartupOutputMirroring}
import org.jetbrains.sbt.{SbtUtil, SbtVersion}

import java.util.concurrent.*
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * Service for connecting with an sbt shell associated with project.
 */
@ApiStatus.Internal()
final class SbtShellCommunication(project: Project) extends SbtShellCommandSubmitter {

  private val stateRef = new AtomicReference[ShellState](ShellState.Off)
  private[shell] def currentState: ShellState = stateRef.get()

  /**
   * @see [[org.jetbrains.sbt.shell.SbtShellStateIntegrationTest.StateSequenceChecker]]
   */
  @TestOnly
  private[shell] var testStateListeners: Seq[ShellState => Unit] = Seq.empty

  @TestOnly
  private[shell] def addTestStateListener(listener: ShellState => Unit): Unit =
    testStateListeners :+= listener

  @TestOnly
  private[shell] def removeTestStateListener(listener: ShellState => Unit): Unit =
    testStateListeners = testStateListeners.filterNot(_ eq listener)

  @TestOnly
  private[shell] def clearTestStateListeners(): Unit =
    testStateListeners = Seq.empty

  // The diagnostics are collected to simplify debugging of test failures
  private lazy val collectDiagnostics: Boolean =
    isInternalMode || isUnitTestMode

  private case class RecordedDiagnosticEvent(timestampMs: Long, event: SbtShellDiagnosticEvent)

  private val diagnosticEvents = new ConcurrentLinkedQueue[RecordedDiagnosticEvent]()

  @TestOnly
  private[shell] def diagnosticEventsSnapshot: Seq[SbtShellDiagnosticEvent] =
    diagnosticEvents.iterator.asScala.map(_.event).toSeq

  @TestOnly
  private[shell] def diagnosticsSnapshot: String = {
    val lifecycle = SbtShellLifecycle.getInstance(project)
    val transitionHistory = lifecycle.transitionHistorySnapshot.takeRight(MaxDiagnosticEvents)
    val events = diagnosticEvents.iterator.asScala.toSeq.takeRight(MaxDiagnosticEvents)
      .map(r => s"${r.timestampMs} ${SbtShellDiagnosticEvent.render(r.event)}")

    s"""SbtShellCommunication diagnostics:
       |currentState=$currentState
       |commandsSize=${commands.size()}
       |afterRestartCommandsSize=${afterRestartCommands.size()}
       |shellWorkingSinceLastReadyPrompt=${shellWorkingSinceLastReadyPrompt.get()}
       |shellQueueReadyPermits=${shellQueueReady.availablePermits()}
       |communicationActivePermits=${communicationActive.availablePermits()}
       |
       |Lifecycle transitions:
       |${if (transitionHistory.nonEmpty) transitionHistory.mkString("\n") else "<empty>"}
       |
       |Recent communication events:
       |${if (events.nonEmpty) events.mkString("\n") else "<empty>"}""".stripMargin
  }

  private lazy val process: SbtProcessManager = SbtProcessManager.forProject(project)

  private val communicationActive = new Semaphore(1)
  private val shellQueueReady = new Semaphore(1)
  /**
   * Stores whether the sbt shell has printed non-prompt output since the last observed ready prompt.
   *
   * When this value is true, the shell may be running a command that was entered outside [[run]], for example
   * manually in the sbt shell tool window. In that state, queued IDEA requests must keep waiting even if they have
   * acquired [[shellQueueReady]], because the next ready prompt is the only signal that the shell can accept another
   * command.
   */
  private val shellWorkingSinceLastReadyPrompt = new AtomicBoolean(true)

  private[shell] case class QueuedCommand(
    request: SbtShellCommandRequest[?],
    listener: SbtShellCommandExecutionOutputListener[?]
  )

  //TODO: rename to commandsQueue
  @VisibleForTesting
  private[shell] val commands = new LinkedBlockingQueue[QueuedCommand]()

  private val queuedStartupOutputMirroring = new SbtShellQueuedStartupOutputMirroring(project)

  /**
   * Buffers commands arriving during soft restart or hard kill.
   *
   * Currently, the concept of a "soft" restart is used only when, during a project reload, it's discovered that the sbt version has changed.
   * In such cases, the shell is restarted in a "soft" way - only after processing all already queued commands.
   *
   * @see [[AfterRestartCommandsGate]]
   * @see [[routeCommandDuringShutdownOrRestart]]
   */
  @VisibleForTesting
  private[shell] val afterRestartCommands = new AfterRestartCommandsGate[QueuedCommand]()

  /**
   * @return sbt version of the running sbt shell (if it's already running)<br>
   *         OR detected sbt version from project/build.properties
   */
  def getRunningOrDetectedSbtVersion: SbtVersion = {
    val sbtVersionRunning = process.sbtVersionUsedDuringProcessStart
    sbtVersionRunning.getOrElse(SbtUtil.detectSbtVersion(project))
  }

  def isRunningAndIdle: Boolean =
    process.isAlive && currentState == SbtShellLifecycle.ShellState.Idle

  def isShuttingDownOrOff: Boolean =
    currentState.isShuttingDownOrOff

  override def run[A](request: SbtShellCommandRequest[A]): Future[A] = {
    val requestId = request.requestId
    Log.debug(s"command start: requestId=$requestId, state=$currentState...")
    recordDiagnosticEvent(SbtShellDiagnosticEvent.RunStart(requestId, currentState))

    val requestNew = request.withProcessorModified(_.tap {
      case ErrorWaitForInput =>
        recordDiagnosticEvent(SbtShellDiagnosticEvent.ErrorWaitForInputDetected(requestId, currentState))
        // When sbt displays an interactive error prompt, automatically send "i" (ignore) to continue
        sendIgnore()
      case _ =>
    })
    val listener = new SbtShellCommandExecutionOutputListener[A](project, requestNew)

    val qc = QueuedCommand(request, listener)
    val shellWasReadyForImmediateSubmission = isRunningAndIdle

    // NOTE:
    // Commands arriving during a soft restart or while the shell is shutting down are routed
    // to the AfterRestartCommandsGate or, if the gate is already closed, to the standard command queue.
    //
    // Once the shell enters `ShuttingDown`, a modal progress window is displayed, so it is
    // unlikely that a new command will be submitted through the UI. An alternative design would
    // be to reject all new commands in the `ShuttingDown` state instead of routing them to a queue.
    // I decided to keep this behavior because it was implemented this way in the past.
    // However, I'm not saying it is the best approach; it can be reviewed in the future if new issues arise.
    if (currentState.isShuttingDown || currentState.isSoftRestarting) {
      routeCommandDuringShutdownOrRestart(qc, request, requestId, shellWasReadyForImmediateSubmission)
    } else {
      // When the shell is in the Off state and a new command is enqueued, EnqueueCommand is emitted here
      // and may be emitted again when the shell becomes ready and the prompt listener observes pending work.
      // Introducing an explicit "Start" state would likely be a solution.

      Log.debug(s"command: enqueue to commands: requestId=$requestId...")
      recordDiagnosticEvent(SbtShellDiagnosticEvent.EnqueueCommands(requestId, shellWasReadyForImmediateSubmission, currentState))

      commands.put(qc)
      notifyQueuedWhileShellBusyIfNeeded(request, shellWasReadyForImmediateSubmission)
      process.acquireShellProcessHandler(request.activateSbtShellToolWindowOnStartup)
      emitShellStateEvent(ShellStateEvent.EnqueueCommand)
    }

    Log.debug(s"### command finish: requestId=$requestId, commandsSize=${commands.size()}, afterRestartSize=${afterRestartCommands.size()}, state=$currentState")
    recordDiagnosticEvent(SbtShellDiagnosticEvent.RunFinish(requestId, commands.size(), afterRestartCommands.size(), currentState))

    listener.future
  }

  /**
   * Routes a command when the shell is in [[ShellState.SoftRestarting]] or [[ShellState.ShuttingDown]] state.
   * Both states use the same routing logic:
   *
   * - If the [[AfterRestartCommandsGate]] is open, the command is buffered for execution after the shell restarts.
   * - If the gate is already closed (buffer was flushed), the command is routed to the standard commands queue,
   *   where it will survive the restart and be processed by the new shell.
   */
  private def routeCommandDuringShutdownOrRestart(
    qc: QueuedCommand,
    request: SbtShellCommandRequest[?],
    requestId: SbtShellCommandRequestId,
    shellWasReadyForImmediateSubmission: Boolean,
  ): Unit =
    if (afterRestartCommands.enqueue(qc)) {
      Log.debug(s"command: enqueue to afterRestartCommands: requestId=$requestId...")
      recordDiagnosticEvent(SbtShellDiagnosticEvent.EnqueueAfterRestartCommands(requestId, shellWasReadyForImmediateSubmission, currentState))
      notifyQueuedWhileShellBusyIfNeeded(request, shellWasReadyForImmediateSubmission)
    } else {
      // Gate closed — route to standard commands queue
      Log.debug(s"command: gate closed, enqueue to commands: requestId=$requestId...")
      recordDiagnosticEvent(SbtShellDiagnosticEvent.EnqueueCommandsAfterGateClosed(requestId, shellWasReadyForImmediateSubmission, currentState))
      commands.put(qc)
      notifyQueuedWhileShellBusyIfNeeded(request, shellWasReadyForImmediateSubmission)
      process.acquireShellProcessHandler(request.activateSbtShellToolWindowOnStartup)
      emitShellStateEvent(ShellStateEvent.EnqueueCommand)
    }

  private def notifyQueuedWhileShellBusyIfNeeded(request: SbtShellCommandRequest[?], shellWasReadyForImmediateSubmission: Boolean): Unit =
    if (!shellWasReadyForImmediateSubmission) {
      try {
        request.onQueuedWhileShellBusy()
      } catch {
        case NonFatal(exception) =>
          Log.warn("Failed to run sbt shell wait notification", exception)
      }
    }

  override def cancel(requestId: SbtShellCommandRequestId): Unit =
    removeCommandFromQueueOrCancel(requestId)

  /** Transitions the shell state to [[ShellState.ShuttingDown]]. */
  private[shell] def enterShuttingDownState(): Unit =
    emitShellStateEvent(ShellStateEvent.ShutdownRequested)

  /**
   * Initiates a hard kill: terminates all commands currently buffered in [[afterRestartCommands]]
   * and transitions the shell to [[ShellState.ShuttingDown]] state.
   *
   * The gate is left open so that commands arriving during the [[ShellState.ShuttingDown]] state
   * are buffered in [[afterRestartCommands]] and later flushed to the standard [[commands]] queue.
   */
  private[shell] def initiateHardKill(): Unit = {
    escalateShutdownToHardKill()
    enterShuttingDownState()
  }

  /**
   * Escalates an already in-progress shutdown to a hard kill: terminates the commands currently buffered
   * in [[afterRestartCommands]] so the shell does not restart after the shutdown completes.
   *
   * This is the buffer-cancelling half of [[initiateHardKill]] without the state transition: it is used
   * when a hard `destroyProcess()` coalesces with a shutdown that is already running (typically a soft
   * restart). The shell is already in [[ShellState.ShuttingDown]], so re-emitting
   * [[ShellStateEvent.ShutdownRequested]] would be a prohibited transition. The gate is left open, matching
   * [[initiateHardKill]], so commands submitted later during shutdown are still handled consistently.
   */
  private[shell] def escalateShutdownToHardKill(): Unit =
    afterRestartCommands.terminateAllAndClear(terminateQueuedCommand)

  /** Terminates and removes all commands currently waiting in the [[commands]] queue. */
  private def terminatePendingCommands(): Unit = {
    commands.forEach(terminateQueuedCommand(_))
    commands.clear()
  }

  private def terminateQueuedCommand(queuedCommand: QueuedCommand): Unit = {
    val QueuedCommand(request, listener) = queuedCommand
    Log.warn(s"Sbt shell is terminated, skipping command: requestId=${request.requestId}")
    recordDiagnosticEvent(SbtShellDiagnosticEvent.TerminatePendingCommand(request.requestId, currentState))
    queuedStartupOutputMirroring.remove(Some(request.requestId))
    listener.processTerminated()
  }

  /**
   * Sends "i" (ignore) to the sbt shell. Works only if the shell is not already in the termination process.
   * Used to handle the interactive error prompt: "Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore".
   *
   * @see [[org.jetbrains.sbt.shell.communication.SbtShellOutputRecognizer.isProjectLoadingPromptError]]
   */
  private def sendIgnore(): Unit = {
    if currentState.isShuttingDownOrOff then
      recordDiagnosticEvent(SbtShellDiagnosticEvent.SendIgnoreSkipped(currentState))
      return

    val sbtVersion = getRunningOrDetectedSbtVersion
    val isNewShell = process.isRunWithNewShell
    val isLinux = SystemInfo.isLinux
    val requiresNewLine = isLoadFailureIgnoreNewlineRequired(sbtVersion, isNewShell, isLinux)
    val command = loadFailureIgnoreCommand(sbtVersion, isNewShell, isLinux)
    recordDiagnosticEvent(SbtShellDiagnosticEvent.SendIgnore(sbtVersion, isNewShell, isLinux, requiresNewLine, command, currentState))

    send(command)
  }

  /**
   * Send string directly to the shell without regarding the shell state.
   * This should only be used to send keypresses such as ctrl+c.
   *
   * ATTENTION!
   *
   * To execute it needs to acquire the shell instance, which may trigger the sbt shell startup.
   */
  def send(keys: String): Unit =
    process.usingWriter { shell =>
      shell.print(keys)
      shell.flush()
    }

  /**
   * Attempts to cancel a queued or running sbt command by its shell command request id.
   *
   * Behavior:
   *  - If the command is found in either the standard commands queue or the [[afterRestartCommands]] queue, it is removed and its listener is terminated
   *  - If the command is not found (likely already running), a cancellation request is sent to the sbt process so the running task can be interrupted.
   */
  def removeCommandFromQueueOrCancel(requestId: SbtShellCommandRequestId): Unit = {
    Log.debug(s"removeCommandFromQueueOrCancel start: requestId=$requestId...")

    def removeFrom(q: LinkedBlockingQueue[QueuedCommand]): QueuedCommand = {
      val it = q.iterator()
      while (it.hasNext) {
        val e = it.next()
        if (e.request.requestId == requestId && q.remove(e)) return e
      }
      null
    }

    var removedElement = removeFrom(commands)
    if (removedElement == null) {
      removedElement = afterRestartCommands.tryRemove(_.request.requestId == requestId).orNull
    }

    if (removedElement != null) {
      Log.debug(s"removeCommandFromQueueOrCancel removed from queue: requestId=$requestId")
      recordDiagnosticEvent(SbtShellDiagnosticEvent.RemoveFromQueue(requestId))
      queuedStartupOutputMirroring.remove(Some(requestId))
      removedElement.listener.processTerminated()
    } else {
      Log.debug(s"removeCommandFromQueueOrCancel not found in queue, requesting process cancellation: requestId=$requestId...")
      recordDiagnosticEvent(SbtShellDiagnosticEvent.CancelRequested(requestId))
      process.requestTaskCancellation()
    }

    Log.debug(s"removeCommandFromQueueOrCancel finish: requestId=$requestId, commandsSize=${commands.size()}, afterRestartSize=${afterRestartCommands.size()}")
  }

  /** Start processing the command queue if it is not yet active. */
  private def startQueueProcessing(handler: OSProcessHandler): Unit = {
    PooledThreadExecutor.INSTANCE.submit(new Runnable {
      override def run(): Unit = try {
        Log.info(s"startQueueProcessing start: state=$currentState...")

        //
        // Main loop of commands queue processing
        //
        def stopProcessingCommandsQueue: Boolean = handler.isProcessTerminating || handler.isProcessTerminated || currentState.isShuttingDown
        while (!stopProcessingCommandsQueue) {
          processNextQueuedCommand(1.second)
        }

        Log.info(s"startQueueProcessing stop loop: handlerTerminating=${handler.isProcessTerminating}, handlerTerminated=${handler.isProcessTerminated}, state=$currentState")
        recordDiagnosticEvent(SbtShellDiagnosticEvent.Trace(
          s"startQueueProcessing stop loop: handlerTerminating=${handler.isProcessTerminating}, handlerTerminated=${handler.isProcessTerminated}, state=$currentState"
        ))

        // If the current state is not shutting down or off, it means the process was terminated externally (e.g., from Activity Monitor),
        // not via `SbtProcessManager.destroyProcess`. In this case, explicitly call `SbtProcessManager.destroyProcess` to properly execute the chain of
        // shell states and cancel the soft restart process if it's running.
        if (!currentState.isShuttingDownOrOff) {
          process.destroyProcess()
        }

        // Process terminated, notify remaining commands in the queue
        // otherwise, there might be some stuck processes
        terminatePendingCommands()

        // release the communicationActive semaphore, before (maybe) acquiring the new shell
        communicationActive.release()

        val pendingCommands = afterRestartCommands.flushAndClose()
        if (!pendingCommands.isEmpty) {
          // Move commands accumulated in `afterRestartCommands` queue to the standard queue (they will be processed when the new shell starts)
          val activateSbtShellToolWindowOnStartup = pendingCommands.asScala.exists(_.request.activateSbtShellToolWindowOnStartup)
          commands.addAll(pendingCommands)
          process.acquireShellProcessHandler(activateSbtShellToolWindowOnStartup)
        }

        Log.info(s"startQueueProcessing finish: commandsSize=${commands.size()}, afterRestartSize=${afterRestartCommands.size()}, state=$currentState")
      } catch {
        case ex: Throwable =>
          Log.error(new RuntimeException("Unexpected exception during commands queue processing", ex))
          throw ex
      }
    })
  }

  private def waitAndProcessNextCommand(timeout: FiniteDuration): Boolean = {
    val nextCommand = commands.poll(timeout.toMillis, TimeUnit.MILLISECONDS)
    if (nextCommand == null)
      false
    else if (shellWorkingSinceLastReadyPrompt.get()) {
      commands.put(nextCommand)
      false
    }
    else {
      processCommand(nextCommand)
      true
    }
  }

  /**
   * ATTENTION: This method is called in a loop until the process is in some terminal state.
   * We need to ensure that all its branches wait for the timeout (unless it's not in the destroying state)
   */
  private def processNextQueuedCommand(timeout: FiniteDuration): Unit = {
    // TODO exception handling
    if (!shellQueueReady.tryAcquire(timeout.toMillis, TimeUnit.MILLISECONDS))
      return

    var isCommandProcessed = false
    try {
      isCommandProcessed = if (currentState.isShuttingDownOrOff)
        false // The new commands will be added to `afterRestartCommands` and processed after the sbt shell is restarted
      else
        waitAndProcessNextCommand(timeout)
    } finally {
      if (isCommandProcessed) {
        // NOTE: when sbt shell executes a command, the `shellQueueReady` is released asynchronously
        // in the `whenReady` callback parameter of `SbtShellReadyLineListener` created in `initCommunication`
      } else if (!shellWorkingSinceLastReadyPrompt.get()) {
        shellQueueReady.release()
      }
    }
  }

  private def shellEventBasedOnCommandsQueue(): ShellStateEvent =
    if (commands.isEmpty) ShellStateEvent.QueueDrained
    else ShellStateEvent.EnqueueCommand

  /**
   * Queue an sbt command for execution in the sbt shell to be performed after a "soft" restart of the shell.
   * A "soft" restart means it waits until all commands currently in the queue are executed before destroying the running process.
   * After the process is destroyed, all commands in [[afterRestartCommands]] are moved to the
   * standard [[commands]], and a new shell instance is acquired.
   *
   * ATTENTION: waiting until all already queued commands are executed through a synchronous wait.
   *
   * @note   Currently, the soft restart is only used during project reload.
   *         In the future, we might also implement a soft restart for tasks executed from the sbt tool window when the sbt version changes.
   * @see    [[org.jetbrains.sbt.shell.SbtProcessManager.destroyProcess]]
   * @return a future completed with the processed command result.
   */
  @RequiresBackgroundThread
  def runAfterSoftRestart[A](request: SbtShellCommandRequest[A]): Future[A] = {
    if (!initiateSoftRestart())
      return run(request)

    // Whether the command queue is empty and the shell is ready
    def allCommandsProcessed: Boolean =
      commands.isEmpty && !shellWorkingSinceLastReadyPrompt.get()

    def waitForAllCommandsInQueueToFinish(): Unit =
      while (!allCommandsProcessed && !currentState.isShuttingDownOrOff) {
        Thread.sleep(1000)
      }

    // The command is put on the `afterRestartCommands` queue
    val commandResultFuture = run(request)

    waitForAllCommandsInQueueToFinish()

    if !currentState.isShuttingDownOrOff then
      SbtProcessManager.forProject(project).softDestroyProcess()

    commandResultFuture
  }

  /**
   * Attempts to initiate a soft restart by emitting [[ShellStateEvent.SoftRestartInitiated]].
   *
   * @return `true` if the soft restart was initiated, `false` if the shell is already shutdown/restart/off.
   */
  private def initiateSoftRestart(): Boolean = {
    val state = currentState
    if state.isSoftRestarting || state.isShuttingDown || state.isOff then
      false
    else
      emitShellStateEvent(ShellStateEvent.SoftRestartInitiated)
      true
  }

  private def processCommand(qc: QueuedCommand): Unit = {
    val QueuedCommand(request, listener) = qc
    val requestId = request.requestId

    Log.debug(s"processCommand start: requestId=$requestId...")
    recordDiagnosticEvent(SbtShellDiagnosticEvent.ProcessCommandStart(requestId, currentState))

    listener.started()

    val handler = process.acquireShellProcessHandler(request.activateSbtShellToolWindowOnStartup)
    handler.addProcessListener(listener)
    queuedStartupOutputMirroring.remove()

    // Set the flag early, before the command text reaches the shell via stdin.
    // Otherwise `runAfterSoftRestart` may see an empty queue and the flag still false
    // (before the shell echoes output back) and destroy the process while this command is still running.
    shellWorkingSinceLastReadyPrompt.set(true)

    process.usingWriter { shell =>
      // Prefix the command with a leading space.
      // In the "new" sbt shell (based on jline3 since sbt 1.4), lines that start with a space are excluded
      // from the history (see HISTORY_IGNORE_SPACE in jline3). This prevents
      // IntelliJ IDEA–generated commands (e.g., reload, build, tasks, test) from cluttering the user's sbt commands history.
      // We keep the same prefix in the "old" shell (jline2) for simplicity - it has no effect and causes no harm there.
      // Consider - maybe not all commands should be escaped e.g. tasks from the sbt tool window.
      // But this is how it used to work in the "old shell".
      val commandText = request.sbtCommandText
      recordDiagnosticEvent(SbtShellDiagnosticEvent.Trace(s"processCommand command text: requestId=$requestId, command=${oneLine(commandText)}"))
      shell.print(s" $commandText")
      // note: the reason why instead of simply doing "shell.println", it was split into command execution and "\n" is Windows
      // and how com.pty4j.windows.winpty.WinPTYOutputStream works
      // (it doesn't impact macos and Linux, because on these systems "\n" is the default new line character).
      // By default, "println" method on Windows add "\r\n", and winpty interprets it as two keypresses (RETURN followed by Ctrl-RETURN).
      // In order to prevent double return pressing, it is enough to send "\n", which results in a single newline call.
      // https://github.com/JetBrains/pty4j/commit/e3e9695066eaddb1994c0081dfbdcd2eb6bd8524
      shell.print("\n")
      shell.flush()
    }

    Log.debug(s"processCommand command sent: requestId=$requestId")
    recordDiagnosticEvent(SbtShellDiagnosticEvent.Trace(s"processCommand command sent: requestId=$requestId, state=$currentState"))

    listener.future.onComplete { result =>
      handler.removeProcessListener(listener)
      Log.debug(s"processCommand finish: requestId=$requestId")
      val resultString = result.fold(
        error => s"failure=${error.getClass.getName}: ${error.getMessage}",
        _ => "success"
      )
      recordDiagnosticEvent(SbtShellDiagnosticEvent.ProcessCommandFinish(requestId, resultString, currentState))
    }
  }

  /**
   * To be called when the process is reinitialized externally.
   * Will only work correctly when `acquireShellProcessHandler.isStartNotify == true`
   * This is usually ensured by calling openShellRunner first, but it's possible
   * to manually trigger it if a fully background process is desired
   */
  private[shell] def initCommunication(handler: OSProcessHandler): Unit = {
    Log.debug(s"initCommunication start: state=$currentState...")

    val lockAcquired = communicationActive.tryAcquire(5, TimeUnit.SECONDS)
    if (lockAcquired) {
      afterRestartCommands.reopen()
      queuedStartupOutputMirroring.registerIfNeeded(handler, queuedStartupOutputOwner)

      // Reset only after communication is acquired: from here on, the ready listener owns the prompt permit for this process.
      shellQueueReady.drainPermits()

      val releaseCommandQueueListener = new SbtShellReadyLineListener(
        "release command queue",
        whenReady = {
          // The process can still flush prompt-shaped output while it is being killed.
          // Do not let that stale callback move the lifecycle back to Idle/Queued.
          val canHandle = canHandlePromptStateChange(handler)
          if (canHandle) {
            shellWorkingSinceLastReadyPrompt.set(false)
            // Emit the event before releasing the permit. Releasing first lets the queue-processing loop poll the next command,
            // so the queue could look empty and emit QueueDrained (-> Idle) instead of EnqueueCommand (-> Queued).
            emitShellStateEvent(shellEventBasedOnCommandsQueue())
            shellQueueReady.release()
          }
          recordDiagnosticEvent(SbtShellDiagnosticEvent.Trace(
            s"ready prompt callback: canHandle=$canHandle, handlerTerminating=${handler.isProcessTerminating}, handlerTerminated=${handler.isProcessTerminated}, state=$currentState"
          ))
        },
        whenWorking = {
          // Ignore late non-prompt output from the terminating process for the same reason.
          val canHandle = canHandlePromptStateChange(handler)
          if (canHandle) {
            onShellStartedWorking()
          }
          recordDiagnosticEvent(SbtShellDiagnosticEvent.Trace(
            s"working output callback: canHandle=$canHandle, handlerTerminating=${handler.isProcessTerminating}, handlerTerminated=${handler.isProcessTerminated}, state=$currentState"
          ))
        },
        project
      )
      handler.addProcessListener(releaseCommandQueueListener)

      handler.addProcessListener(new InitialErrorDetectorListener())
      startQueueProcessing(handler)

      Log.debug(s"initCommunication finish: communication activated, state=$currentState")
    } else {
      Log.debug(s"initCommunication finish: communication NOT activated (lock couldn't be acquired), state=$currentState")
    }
  }

  private def canHandlePromptStateChange(handler: OSProcessHandler): Boolean =
    !handler.isProcessTerminating && !handler.isProcessTerminated && !currentState.isShuttingDown

  private def queuedStartupOutputOwner: Option[Owner] =
    commands.iterator().asScala.collectFirst {
      case QueuedCommand(request, listener) if request.mirrorQueuedOutput =>
        Owner(request.requestId, listener.processQueuedOutput)
    }

  private def onShellStartedWorking(): Unit = {
    shellWorkingSinceLastReadyPrompt.set(true)
    shellQueueReady.drainPermits()
    if (currentState.isIdle) {
      emitShellStateEvent(ShellStateEvent.ShellBecameBusy)
    }
  }

  def emitShellStateEvent(event: ShellStateEvent): Unit = {
    val previous = currentState
    val next = SbtShellLifecycle.getInstance(project).transition(currentState, event)
    stateRef.set(next)
    recordDiagnosticEvent(SbtShellDiagnosticEvent.Trace(s"emitShellStateEvent: $previous --$event--> $next"))
    testStateListeners.foreach(_(next))
  }

  /**
   * Listener that sends "i" (ignore) to the sbt shell when an interactive error prompt appears during startup.
   * This is considered "initial" because it only works until the shell becomes ready.
   * Handling of interactive error prompts during specific commands is managed by [[SbtShellCommandExecutionOutputListener]].
   *
   * @see [[org.jetbrains.sbt.shell.communication.SbtShellOutputRecognizer.isProjectLoadingPromptError]]
   */
  private class InitialErrorDetectorListener
    extends SbtOutputCompleteLinesProcessListener(project) {

    private var isReadyState: Boolean = false

    override def onLine(line: String): Unit =
      if (SbtShellOutputRecognizer.isPromptReady(line, isNewSbtShell)) {
        recordDiagnosticEvent(SbtShellDiagnosticEvent.Trace(s"initial listener ready prompt: line=${oneLine(line)}"))
        isReadyState = true
      } else if (!isReadyState && SbtShellOutputRecognizer.isProjectLoadingPromptError(line)) {
        recordDiagnosticEvent(SbtShellDiagnosticEvent.Trace(s"initial listener project loading prompt: line=${oneLine(line)}"))
        sendIgnore()
      }
  }

  private def recordDiagnosticEvent(event: SbtShellDiagnosticEvent): Unit =
    if (collectDiagnostics) {
      diagnosticEvents.add(RecordedDiagnosticEvent(System.currentTimeMillis(), event))
    }

  private def oneLine(text: String): String = {
    val normalized = Option(text).getOrElse("<null>").replace("\r", "\\r").replace("\n", "\\n")
    if (normalized.length <= MaxDiagnosticTextLength) normalized
    else normalized.take(MaxDiagnosticTextLength) + "...<truncated>"
  }
}

object SbtShellCommunication {
  private val Log: Logger = Logger.getInstance(classOf[SbtShellCommunication])
  private val MaxDiagnosticEvents = 200
  private val MaxDiagnosticTextLength = 500
  private val SbtVersionWithRawLoadFailureInput = SbtVersion("1.4.0")

  private[shell] def loadFailureIgnoreCommand(
    sbtVersion: SbtVersion,
    isNewShell: Boolean,
    isLinux: Boolean,
    lineSeparator: String = System.lineSeparator,
  ): String = {
    val withNewLineAfter = isLoadFailureIgnoreNewlineRequired(sbtVersion, isNewShell, isLinux)
    if (withNewLineAfter)
      "i" + lineSeparator
    else
      "i"
  }

  private[shell] def isLoadFailureIgnoreNewlineRequired(
    sbtVersion: SbtVersion,
    isNewShell: Boolean,
    isLinux: Boolean,
  ): Boolean = {
    // SCL-25342, SCL-24349: sbt 1.4+ reads one raw byte after printing the failed-load prompt
    // (https://github.com/sbt/sbt/commit/5afc0f0fdfe4500770c000a02fa57c9b46e8de3c).
    // On Linux with the legacy idea-shell PTY, IDEA can observe the prompt and write `i` before sbt
    // enters raw input mode, so the byte is echoed and not consumed as "ignore" without a newline.
    val isLegacyLinuxShell = isLinux && !isNewShell

    sbtVersion < SbtVersionWithRawLoadFailureInput || isLegacyLinuxShell
  }

  def forProject(project: Project): SbtShellCommunication =
    SbtShellCommandSubmitter.instance(project).asInstanceOf[SbtShellCommunication]
}
