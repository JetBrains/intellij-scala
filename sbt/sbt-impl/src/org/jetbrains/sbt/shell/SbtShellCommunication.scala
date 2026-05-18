package org.jetbrains.sbt.shell

import com.intellij.build.events.impl.{FailureResultImpl, SuccessResultImpl}
import com.intellij.execution.process.{OSProcessHandler, ProcessEvent}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.{ApiStatus, Nls, NonNls, TestOnly}
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.sbt.shell.SbtShellCommunication.*
import org.jetbrains.sbt.shell.communication.SbtShellLifecycle.{ShellState, ShellStateEvent}
import org.jetbrains.sbt.shell.communication.{SbtOutputCompleteLinesProcessListener, SbtProcessUtil, SbtShellLifecycle}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion}

import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
 * Service for connecting with an sbt shell associated with project.
 */
@Service(Array(Service.Level.PROJECT))
@ApiStatus.Internal()
final class SbtShellCommunication(project: Project) {

  private val log = Logger.getInstance(getClass)

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

  private case class QueuedCommand(id: String, cmd: () => String, listener: CommandListener[?])

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

  /** Queue an sbt command for execution in the sbt shell, returning a Future[String] containing the entire shell output. */
  def command(cmd: String): Future[String] =
    command(cmd, new StringBuilder(), messageAggregator).map(_.toString())

  def command(cmd: => String, id: String): Future[String] =
    command(cmd, id, new StringBuilder(), messageAggregator, None).map(_.toString())

  /** Queue an sbt command for execution in the sbt shell. */
  def command[A](@NonNls cmd: => String, default: A, eventHandler: EventAggregator[A], terminationMessage: Option[String] = None): Future[A] =
    command(cmd, id = UUID.randomUUID().toString, default, eventHandler, terminationMessage)

  def command[A](
    @NonNls cmd: => String,
    id: String,
    default: A,
    eventHandler: EventAggregator[A],
    terminationMessage: Option[String]
  ): Future[A] = {
    log.debug(s"command start: id=$id, state=$currentState...")

    val listener = new CommandListener(default, eventHandler, terminationMessage)

    val qc = QueuedCommand(id, () => cmd, listener)

    if (isDestroyingOrEmptyingQueueInProgress) {
      log.debug(s"command: enqueue to afterRestartCommands: id=$id...")

      afterRestartCommands.put(qc)
    } else {
      // TODO it's some imperfection at this place to address in SCL-24338
      // When the shell is in the Off state and a new command is enqueued, EnqueueCommand is emitted three times:
      // during #initCommunication, when the shell becomes ready, and here.
      // Introducing an explicit "Start" state would likely be a solution.

      log.debug(s"command: enqueue to commands: id=$id...")

      commands.put(qc)
      process.acquireShellProcessHandler()
      emitShellStateEvent(ShellStateEvent.EnqueueCommand)
    }

    log.debug(s"### command finish: id=$id, commandsSize=${commands.size()}, afterRestartSize=${afterRestartCommands.size()}, state=$currentState")

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
    commandsQueue.forEach { case QueuedCommand(_, cmd, listener) =>
      Log.warn(s"Sbt shell is terminated, skipping command: $cmd")
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
   * Attempts to cancel a queued sbt command by its id.
   *
   * Behavior:
   *  - If the command is found in either the standard commands queue or the [[afterRestartCommands]] queue, it is removed and its listener is terminated
   *  - If the command is not found (likely already running), a cancellation request is sent to the sbt process so the running task can be interrupted.
   */
  def removeCommandFromQueueOrCancel(id: String): Unit = {
    log.debug(s"removeCommandFromQueueOrCancel start: id=$id...")

    def removeFrom(q: LinkedBlockingQueue[QueuedCommand]): QueuedCommand = {
      val it = q.iterator()
      while (it.hasNext) {
        val e = it.next()
        if (e.id == id && q.remove(e)) return e
      }
      null
    }

    var removedElement = removeFrom(commands)
    if (removedElement == null) {
      removedElement = removeFrom(afterRestartCommands)
    }

    if (removedElement != null) {
      log.debug(s"removeCommandFromQueueOrCancel removed from queue: id=$id")
      removedElement.listener.processTerminated()
    } else {
      log.debug(s"removeCommandFromQueueOrCancel not found in queue, requesting process cancellation: id=$id...")
      process.requestTaskCancellation()
    }

    log.debug(s"removeCommandFromQueueOrCancel finish: id=$id, commandsSize=${commands.size()}, afterRestartSize=${afterRestartCommands.size()}")
  }

  private def isDestroyingOrEmptyingQueueInProgress: Boolean =
    currentState.isShuttingDown || isEmptyingQueueRunning

  private def isEmptyingQueueRunning: Boolean =
    Option(emptyingQueueFuture.get()).exists(!_.isDone)

  /** Start processing command queue if it is not yet active. */
  private def startQueueProcessing(handler: OSProcessHandler): Unit = {
    PooledThreadExecutor.INSTANCE.submit(new Runnable {
      override def run(): Unit = try {
        log.info(s"startQueueProcessing start: state=$currentState...")

        // queue ready signal is given by initCommunication.stateChanger
        shellQueueReady.drainPermits()

        //
        // Main loop of commands queue processing
        //
        def stopProcessingCommandsQueue: Boolean = handler.isProcessTerminating || handler.isProcessTerminated || currentState.isShuttingDown
        while (!stopProcessingCommandsQueue) {
          processNextQueuedCommand(1.second)
        }

        log.info(s"startQueueProcessing stop loop: handlerTerminating=${handler.isProcessTerminating}, handlerTerminated=${handler.isProcessTerminated}, state=$currentState")

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

        log.info(s"startQueueProcessing finish: commandsSize=${commands.size()}, afterRestartSize=${afterRestartCommands.size()}, state=$currentState")
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
   * @return `Future[String]` containing the entire shell output
   */
  @RequiresBackgroundThread
  def commandAfterSoftRestart[A](cmd: => String, default: A, eventHandler: EventAggregator[A], terminationMessage: String): Future[A] = {
    if (isEmptyingQueueRunning)
      return command(cmd, default, eventHandler, Some(terminationMessage))

    val emptyingQueue = new CompletableFuture[Unit]()

    def waitForAllCommandsInQueueToFinish(): Unit =
      while (currentState.isQueued && !currentState.isShuttingDownOrOff) {
        Thread.sleep(1000)
      }

    emptyingQueueFuture.set(emptyingQueue)

    // The command is put on the `afterRestartCommands` queue
    val commandResultFuture = command(cmd, default, eventHandler, Some(terminationMessage))
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
    val QueuedCommand(id, cmd, listener) = qc

    log.debug(s"processCommand start: id=$id...")

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
      shell.print(s" ${cmd()}")
      // note: the reason why instead of simply doing "shell.println", it was split into command execution and "\n" is Windows
      // and how com.pty4j.windows.winpty.WinPTYOutputStream works
      // (it doesn't impact macos and Linux, because on these systems "\n" is the default new line character).
      // By default, "println" method on Windows add "\r\n", and winpty interprets it as two keypresses (RETURN followed by Ctrl-RETURN).
      // In order to prevent double return pressing, it is enough to send "\n", which results in a single newline call.
      // https://github.com/JetBrains/pty4j/commit/e3e9695066eaddb1994c0081dfbdcd2eb6bd8524
      shell.print("\n")
      shell.flush()
    }

    log.debug(s"processCommand command sent: id=$id")

    listener.future.onComplete { _ =>
      handler.removeProcessListener(listener)
      log.debug(s"processCommand finish: id=$id")
    }
  }

  /**
   * To be called when the process is reinitialized externally.
   * Will only work correctly when `acquireShellProcessHandler.isStartNotify == true`
   * This is usually ensured by calling openShellRunner first, but it's possible
   * to manually trigger it if a fully background process is desired
   */
  private[shell] def initCommunication(handler: OSProcessHandler): Unit = {
    log.debug(s"initCommunication start: state=$currentState...")

    val lockAcquired = communicationActive.tryAcquire(5, TimeUnit.SECONDS)
    if (lockAcquired) {
      val releaseCommandQueueListener = new SbtShellReadyLineListener(
        "release command queue",
        whenReady = {
          shellQueueReady.release()
          emitShellStateEvent(shellEventBasedOnCommandsQueue())
        },
        whenWorking = (),
        project
      )
      emitShellStateEvent(shellEventBasedOnCommandsQueue())
      handler.addProcessListener(releaseCommandQueueListener)

      handler.addProcessListener(new InitialErrorDetectorListener())
      startQueueProcessing(handler)

      log.debug(s"initCommunication finish: communication activated, state=$currentState")
    } else {
      log.debug(s"initCommunication finish: communication NOT activated (lock couldn't be acquired), state=$currentState")
    }
  }

  def emitShellStateEvent(event: ShellStateEvent): Unit = {
    val next = SbtShellLifecycle.transition(currentState, event)
    stateRef.set(next)
    testStateListener.foreach(_(next))
  }

  private[sbt] def messageAggregatorForSync(
    reporter: BuildReporter,
    dumpTaskId: EventId,
    processOutputBuilder: Option[StringBuilder],
    @Nls startMessage: String,
    @Nls finishMessage: String
  ): EventAggregator[BuildMessages] =
    messageAggregatorWithReporter(
      reporter, dumpTaskId, processOutputBuilder, startMessage, finishMessage,
      onOutputLine = _ => (),
      showSbtShellOnError = false
    )

  private[sbt] def messageAggregatorForBuild(
    reporter: BuildReporter,
    dumpTaskId: EventId,
    processOutputBuilder: Option[StringBuilder],
    @Nls startMessage: String,
    @Nls finishMessage: String,
    onOutputLine: String => Unit
  ): EventAggregator[BuildMessages] =
    messageAggregatorWithReporter(
      reporter, dumpTaskId, processOutputBuilder, startMessage, finishMessage, onOutputLine, showSbtShellOnError = true
    )

  private def messageAggregatorWithReporter(
    reporter: BuildReporter,
    dumpTaskId: EventId,
    processOutputBuilder: Option[StringBuilder],
    @Nls startMessage: String,
    @Nls finishMessage: String,
    onOutputLine: String => Unit,
    showSbtShellOnError: Boolean,
  ): EventAggregator[BuildMessages] = (messages, event) => event match {
    case TaskStart =>
      log.trace(s"messageAggregator TaskStart: dumpTaskId=$dumpTaskId...")

      reporter.startTask(dumpTaskId, None, startMessage)
      messages

    case TaskComplete =>
      log.trace(s"messageAggregator TaskComplete: dumpTaskId=$dumpTaskId")

      reporter.finishTask(dumpTaskId, finishMessage, new SuccessResultImpl())
      val messagesUpdated =
        if (messages.status == BuildMessages.Indeterminate) messages.status(BuildMessages.OK)
        else messages
      messagesUpdated

    case ProcessTerminated =>
      log.trace(s"messageAggregator ProcessTerminated: dumpTaskId=$dumpTaskId")

      //TODO: it seems like in practice "process terminated" is not used at all
      // we need to refactor the reporter API to not demand it
      reporter.finishTask(dumpTaskId, "process terminated", new SuccessResultImpl())
      messages
        .addError("process terminated")
        .status(BuildMessages.Canceled)

    case ErrorWaitForInput =>
      log.trace(s"messageAggregator ErrorWaitForInput: dumpTaskId=$dumpTaskId")

      val msg = SbtBundle.message("sbt.import.errors.project.reload.aborted")
      val ex = new ExternalSystemException(msg)

      val result = new FailureResultImpl(msg, ex)
      reporter.finishTask(dumpTaskId, msg, result)

      messages.addError(msg)

    case Output(raw) =>
      // Strip ANSI codes in both old and new sbt shell modes for simplicity - it's harmless in old mode.
      val text = BuildMessages.stripAnsiCodes(raw).trim
      if (log.isTraceEnabled) {
        log.trace(s"messageAggregator Output: dumpTaskId=$dumpTaskId, text=$text")
      }

      processOutputBuilder.foreach(_.append(text))

      val isError = isErrorOutput(text)
      val newMessages =
        if (isError) {
          if (messages.errors.isEmpty && showSbtShellOnError) {
            SbtShellRunner.openShell(focus = false, project)
          }
          messages.addError(text.stripPrefix(ERROR_PREFIX))
        } else if (text `startsWith` WARN_PREFIX) {
          messages.addWarning(text.stripPrefix(WARN_PREFIX))
        } else messages

      onOutputLine(text)

      reporter.progressTask(dumpTaskId, 1, -1, SbtBundle.message("sbt.events"), text)

      if (isError) {
        reporter.logErr(text)
      } else {
        reporter.log(text)
      }

      newMessages
  }

  /**
   * Listener that sends "i" (ignore) to the sbt shell when an interactive error prompt appears during startup.
   * This is considered "initial" because it only works until the shell becomes ready.
   * Handling of interactive error prompts during specific commands is managed by [[org.jetbrains.sbt.shell.CommandListener.onLine]].
   *
   * @see [[org.jetbrains.sbt.shell.SbtProcessUtil.promptError]]
   */
  private class InitialErrorDetectorListener
    extends SbtOutputCompleteLinesProcessListener(project) {

    private var isReadyState: Boolean = false

    override def onLine(line: String): Unit =
      if (SbtProcessUtil.promptReady(line, isNewSbtShell))
        isReadyState = true
      else if (!isReadyState && SbtProcessUtil.promptError(line))
        sendIgnore()
  }

  private class CommandListener[A](
    default: A,
    aggregator: EventAggregator[A],
    terminationMessage: Option[String] = None
  ) extends SbtOutputCompleteLinesProcessListener(project) {

    private val promise = Promise[A]()
    private var a: A = default

    private def aggregate(event: ShellEvent): Unit = {
      a = aggregator(a, event)
    }

    def future: Future[A] = promise.future

    def started(): Unit = {
      this.log.debug("CommandListener.started")
      aggregate(TaskStart)
    }

    override def processTerminated(event: ProcessEvent): Unit = {
      this.log.debug(s"CommandListener.processTerminated(exitCode=${event.getExitCode}, text=${event.getText})")
      processTerminated()
    }

    def processTerminated(): Unit = {
      this.log.debug("CommandListener.processTerminated")
      aggregate(ProcessTerminated)
      val message = terminationMessage.getOrElse("Sbt shell terminated before command is finished")
      promise.complete(Failure(new RuntimeException(message)))
    }

    override def onLine(text: String): Unit = {
      val shouldCompleteTask = !promise.isCompleted && SbtProcessUtil.promptReady(text, isNewSbtShell)
      if (shouldCompleteTask) {
        this.log.trace("CommandListener.onLine: promptReady -> TaskComplete")

        aggregate(TaskComplete)
        promise.complete(Success(a))
      } else if (SbtProcessUtil.promptError(text)) {
        this.log.trace("CommandListener.onLine: promptError detected -> sendIgnore...")

        // When sbt displays an interactive error prompt, automatically send "i" (ignore) to continue
        sendIgnore()
        aggregate(ErrorWaitForInput)
      } else {
        if (this.log.isTraceEnabled)
          this.log.trace(s"CommandListener.onLine: output line: $text")

        aggregate(Output(text))
      }
    }
  }
}

object SbtShellCommunication {
  protected val Log: Logger = Logger.getInstance(getClass)

  private val WARN_PREFIX = "[warn]"
  private val ERROR_PREFIX = "[error]"

  def forProject(project: Project): SbtShellCommunication = project.getService(classOf[SbtShellCommunication])

  sealed trait ShellEvent
  case object TaskStart extends ShellEvent
  case object TaskComplete extends ShellEvent
  case object ProcessTerminated extends ShellEvent
  case object ErrorWaitForInput extends ShellEvent
  case class Output(line: String) extends ShellEvent

  sealed trait ErrorReaction
  case object Quit extends ErrorReaction
  case object Ignore extends ErrorReaction

  type EventAggregator[A] = (A, ShellEvent) => A

  /** Aggregates the output of a shell task. */
  private val messageAggregator: EventAggregator[StringBuilder] = (builder, e) => e match {
    case TaskStart |
         TaskComplete |
         ProcessTerminated |
         ErrorWaitForInput =>
      builder
    case Output(text) =>
      builder.append("\n").append(text)
  }

  /** Convenience aggregator wrapper that is executed for the side effects.
   * The final result will just be the value of the last invocation. */
  def listenerAggregator[A](listener: ShellEvent => A): EventAggregator[A] = (_,e) =>
    listener(e)

  /**
   * @param sbtOutputText a line of output from the sbt shell
   * @return true if the line starts with `[error]`
   * @note technically it's not entirely correct way to detect if the output is "an error".
   *       A user can still print some text to stdout that would start with `[error]` that would not be a "sbt error".
   *       But to our latest knowledge, there is no better way to reliably get that with the way current sbt-shell communication
   *       is implemented.
   */
  def isErrorOutput(sbtOutputText: String): Boolean =
    sbtOutputText.startsWith(ERROR_PREFIX)
}
