package org.jetbrains.sbt.shell

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.{ApiStatus, TestOnly}
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.sbt.shell.SbtShellCommunication.*
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.{ShellState, ShellStateEvent}
import org.jetbrains.sbt.shell.communication.ShellEvent.ErrorWaitForInput
import org.jetbrains.sbt.shell.communication.{SbtOutputCompleteLinesProcessListener, SbtProcessUtil, SbtShellCommandExecutionOutputListener, SbtShellCommandRequest, SbtShellCommandRequestId, SbtShellCommandSubmitter, SbtShellLifecycle}
import org.jetbrains.sbt.{SbtUtil, SbtVersion}

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

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
  private[shell] var testStateListener: Option[ShellState => Unit] = None

  @TestOnly
  private[shell] def setTestStateListener(listener: ShellState => Unit): Unit =
    testStateListener = Some(listener)

  @TestOnly
  private[shell] def clearTestStateListener(): Unit =
    testStateListener = None

  private lazy val process: SbtProcessManager = SbtProcessManager.forProject(project)

  private val communicationActive = new Semaphore(1)
  private val shellQueueReady = new Semaphore(1)

  private case class QueuedCommand(
    request: SbtShellCommandRequest[?],
    listener: SbtShellCommandExecutionOutputListener[?]
  )

  //TODO: rename to commandsQueue
  private val commands = new LinkedBlockingQueue[QueuedCommand]()

  /**
   * The queue for commands accumulated when the shell is in the process of emptying standard queue commands before the "soft" restart or destroying.
   * Currently, the concept of a "soft" restart is used only when, during a project reload, it's discovered that the sbt version has changed.
   * In such cases, the shell is restarted in a "soft" way - only after processing all already queued commands.
   *
   * If, during emptying the queue, the sbt shell is manually killed by the user or via the dispose method,
   * all commands accumulated in this queue are moved to the standard [[commands]] queue
   * and are terminated in queue processing method ([[startQueueProcessing]]).
   */
  private val afterRestartCommands = new LinkedBlockingQueue[QueuedCommand]()

  /**
   * Contains an atomic reference to a `Future` responsible for emptying [[commands]] queue.
   *
   * @todo extract to a separate state SCL-24338
   */
  private val emptyingQueueFuture = new AtomicReference[CompletableFuture[Unit]](null)

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

  override def run[A](request: SbtShellCommandRequest[A]): Future[A] = {
    val requestId = request.requestId
    Log.debug(s"command start: requestId=$requestId, state=$currentState...")

    val requestNew = request.withProcessorModified(_.tap {
      case ErrorWaitForInput =>
        // When sbt displays an interactive error prompt, automatically send "i" (ignore) to continue
        sendIgnore()
      case _ =>
    })
    val listener = new SbtShellCommandExecutionOutputListener[A](project, requestNew)

    val qc = QueuedCommand(request, listener)

    if (isDestroyingOrEmptyingQueueInProgress) {
      Log.debug(s"command: enqueue to afterRestartCommands: requestId=$requestId...")

      afterRestartCommands.put(qc)
    } else {
      // TODO it's some imperfection at this place to address in SCL-24338
      // When the shell is in the Off state and a new command is enqueued, EnqueueCommand is emitted here
      // and may be emitted again when the shell becomes ready and the prompt listener observes pending work.
      // Introducing an explicit "Start" state would likely be a solution.

      Log.debug(s"command: enqueue to commands: requestId=$requestId...")

      commands.put(qc)
      process.acquireShellProcessHandler()
      emitShellStateEvent(ShellStateEvent.EnqueueCommand)
    }

    Log.debug(s"### command finish: requestId=$requestId, commandsSize=${commands.size()}, afterRestartSize=${afterRestartCommands.size()}, state=$currentState")

    listener.future
  }

  /**
   * Cancels the upcoming soft restart by interrupting the queue-emptying future
   * and terminating all commands accumulated in [[afterRestartCommands]].
   */
  private[shell] def cancelSoftRestartProcess(): Unit = {
    Option(emptyingQueueFuture.get()).foreach(_.cancel(true))
    terminatePendingCommands(afterRestartCommands)
  }

  private def terminatePendingCommands(commandsQueue: LinkedBlockingQueue[QueuedCommand]): Unit = {
    commandsQueue.forEach { case QueuedCommand(request, listener) =>
      Log.warn(s"Sbt shell is terminated, skipping command: requestId=${request.requestId}")
      listener.processTerminated()
    }
    commandsQueue.clear()
  }

  /**
   * Sends "i" (ignore) to the sbt shell. Works only if the shell is not already in the termination process.
   * Used to handle the interactive error prompt: "Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore".
   *
   * @see [[org.jetbrains.sbt.shell.SbtProcessUtil.promptError]]
   */
  private def sendIgnore(): Unit = {
    if currentState.isShuttingDownOrOff then return

    // Prior to sbt 1.4.0, the load failure command input required a newline.
    // However, in newer versions, adding it unconditionally causes a double prompt to appear.
    // See https://github.com/sbt/sbt/commit/5afc0f0fdfe4500770c000a02fa57c9b46e8de3c
    val requiresNewLine = getRunningOrDetectedSbtVersion < SbtVersion("1.4.0")
    val command =
      if (requiresNewLine) "i" + System.lineSeparator
      else "i"
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
      removedElement = removeFrom(afterRestartCommands)
    }

    if (removedElement != null) {
      Log.debug(s"removeCommandFromQueueOrCancel removed from queue: requestId=$requestId")
      removedElement.listener.processTerminated()
    } else {
      Log.debug(s"removeCommandFromQueueOrCancel not found in queue, requesting process cancellation: requestId=$requestId...")
      process.requestTaskCancellation()
    }

    Log.debug(s"removeCommandFromQueueOrCancel finish: requestId=$requestId, commandsSize=${commands.size()}, afterRestartSize=${afterRestartCommands.size()}")
  }

  private def isDestroyingOrEmptyingQueueInProgress: Boolean =
    currentState.isShuttingDown || isEmptyingQueueRunning

  private def isEmptyingQueueRunning: Boolean =
    Option(emptyingQueueFuture.get()).exists(!_.isDone)

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

        // If the current state is not shutting down or off, it means the process was terminated externally (e.g., from Activity Monitor),
        // not via `SbtProcessManager.destroyProcess`. In this case, explicitly call `SbtProcessManager.destroyProcess` to properly execute the chain of
        // shell states and cancel the soft restart process if it's running.
        if (!currentState.isShuttingDownOrOff) {
          process.destroyProcess()
        }

        // Process terminated, notify remaining commands in the queue
        // otherwise, there might be some stuck processes
        terminatePendingCommands(commands)

        // release the communicationActive semaphore, before (maybe) acquiring the new shell
        communicationActive.release()

        if (!afterRestartCommands.isEmpty) {
          // Move commands accumulated in `afterRestartCommands` queue to the standard queue (they will be processed when the new shell starts)
          afterRestartCommands.drainTo(commands)
          process.acquireShellProcessHandler()
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
        false // The new commands will be added to another queue `afterRestartCommands` and processed after the sbt shell is restarted
      else
        waitAndProcessNextCommand(timeout)
    } finally {
      if (isCommandProcessed) {
        // NOTE: when sbt shell executes a command, the `shellQueueReady` is released asynchronously
        // in the `whenReady` callback parameter of `SbtShellReadyLineListener` created in `initCommunication`
      } else {
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
    if (isEmptyingQueueRunning)
      return run(request)

    val emptyingQueue = new CompletableFuture[Unit]()

    def waitForAllCommandsInQueueToFinish(): Unit =
      while (currentState.isQueued && !currentState.isShuttingDownOrOff) {
        Thread.sleep(1000)
      }

    emptyingQueueFuture.set(emptyingQueue)

    // The command is put on the `afterRestartCommands` queue
    val commandResultFuture = run(request)
    try {
      waitForAllCommandsInQueueToFinish()

      if (!emptyingQueue.isCompletedExceptionally) {
        emptyingQueue.complete(())
        SbtProcessManager.forProject(project).softDestroyProcess()
      }

      commandResultFuture
    } finally {
      emptyingQueueFuture.set(null)
    }
  }

  private def processCommand(qc: QueuedCommand): Unit = {
    val QueuedCommand(request, listener) = qc
    val requestId = request.requestId

    Log.debug(s"processCommand start: requestId=$requestId...")

    listener.started()

    val handler = process.acquireShellProcessHandler()
    handler.addProcessListener(listener)

    process.usingWriter { shell =>
      // Prefix the command with a leading space.
      // In the "new" sbt shell (based on jline3 since sbt 1.4), lines that start with a space are excluded
      // from the history (see HISTORY_IGNORE_SPACE in jline3). This prevents
      // IntelliJ IDEA–generated commands (e.g., reload, build, tasks, test) from cluttering the user's sbt commands history.
      // We keep the same prefix in the "old" shell (jline2) for simplicity - it has no effect and causes no harm there.
      // Consider - maybe not all commands should be escaped e.g. tasks from the sbt tool window.
      // But this is how it used to work in the "old shell".
      val commandText = request.sbtCommandText
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

    listener.future.onComplete { _ =>
      handler.removeProcessListener(listener)
      Log.debug(s"processCommand finish: requestId=$requestId")
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
      // Reset only after communication is acquired: from here on, the ready listener owns the prompt permit for this process.
      shellQueueReady.drainPermits()

      val releaseCommandQueueListener = new SbtShellReadyLineListener(
        "release command queue",
        whenReady = {
          shellQueueReady.release()
          emitShellStateEvent(shellEventBasedOnCommandsQueue())
        },
        whenWorking = (),
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

  def emitShellStateEvent(event: ShellStateEvent): Unit = {
    val next = SbtShellLifecycle.transition(currentState, event)
    stateRef.set(next)
    testStateListener.foreach(_(next))
  }

  /**
   * Listener that sends "i" (ignore) to the sbt shell when an interactive error prompt appears during startup.
   * This is considered "initial" because it only works until the shell becomes ready.
   * Handling of interactive error prompts during specific commands is managed by [[SbtShellCommandExecutionOutputListener]].
   *
   * @see [[org.jetbrains.sbt.shell.SbtProcessUtil.promptError]]
   */
  private class InitialErrorDetectorListener
    extends SbtOutputCompleteLinesProcessListener(project) {

    private var isReadyState: Boolean = false

    override def onLine(line: String): Unit =
      if (SbtProcessUtil.promptReady(line, isNewSbtShell)) {
        isReadyState = true
      } else if (!isReadyState && SbtProcessUtil.promptError(line)) {
        sendIgnore()
      }
  }
}

object SbtShellCommunication {
  private val Log: Logger = Logger.getInstance(classOf[SbtShellCommunication])

  def forProject(project: Project): SbtShellCommunication =
    SbtShellCommandSubmitter.instance(project).asInstanceOf[SbtShellCommunication]
}
