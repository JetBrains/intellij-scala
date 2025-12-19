package org.jetbrains.sbt.shell

import com.intellij.build.events.impl.{FailureResultImpl, SuccessResultImpl}
import com.intellij.execution.process.{AnsiEscapeDecoder, OSProcessHandler, ProcessEvent, ProcessListener}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.{ApiStatus, Nls, NonNls, TestOnly}
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildReporter}
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.plugins.scala.isInternalMode
import org.jetbrains.sbt.shell.LineListener.{LineSeparatorRegex, escapeNewLines}
import org.jetbrains.sbt.shell.SbtProcessUtil.*
import org.jetbrains.sbt.shell.SbtShellCommunication.*
import org.jetbrains.sbt.shell.SbtShellLifecycle.{ShellState, ShellStateEvent}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, SbtVersion}

import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

// TODO: this class has become too complicated, too much random state updates.
//  We need to design a better architecture for it.
//  Finite state machine would be a good fit here
/**
 * Service for connecting with an sbt shell associated with project.
 */
@Service(Array(Service.Level.PROJECT))
@ApiStatus.Internal()
final class SbtShellCommunication(project: Project) {

  private val stateRef = new AtomicReference[ShellState](ShellState.Off)
  private def currentState: ShellState = stateRef.get()

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

  /** Queue an sbt command for execution in the sbt shell, returning a Future[String] containing the entire shell output. */
  def command(cmd: String): Future[String] =
    command(cmd, new StringBuilder(), messageAggregator).map(_.toString())

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
    val listener = new CommandListener(default, eventHandler, terminationMessage)

    val qc = QueuedCommand(id, () => cmd, listener)

    if (isDestroyingOrEmptyingQueueInProgress) {
      afterRestartCommands.put(qc)
    } else {
      // TODO it's some imperfection at this place to address in SCL-24338
      // When the shell is in the Off state and a new command is enqueued, EnqueueCommand is emitted three times:
      // during #initCommunication, when the shell becomes ready, and here.
      // Introducing an explicit "Start" state would likely be a solution.
      commands.put(qc)
      process.acquireShellProcessHandler()
      emitShellStateEvent(ShellStateEvent.EnqueueCommand)
    }

    listener.future
  }

  /**
   * Cancel the queue emptying process and transfers any pending commands from the [[afterRestartCommands]] queue
   * to the standard [[commands]] queue.
   * These transferred commands will be terminated during the standard queue processing shutdown.
   */
  def cancelEmptyingQueue(): Unit = {
    Option(emptyingQueueFuture.get()).foreach(_.cancel(true))
    moveAccumulatedCommandsToStandardQueue()
  }

  /**
   * Move commands accumulated in [[afterRestartCommands]] queue to the standard [[commands]] queue.
   */
  private def moveAccumulatedCommandsToStandardQueue(): Int =
    afterRestartCommands.drainTo(commands)

  /**
   * Sends "i" (ignore) to the sbt shell.
   * Used to handle the interactive error prompt: "Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore".
   *
   * @see [[org.jetbrains.sbt.shell.SbtProcessUtil.promptError]]
   */
  private def sendIgnore(): Unit = {
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
    * This should only be used to send keypresses such as ctrl+c
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
      removedElement.listener.processTerminated()
    } else {
      process.requestTaskCancellation()
    }
  }

  private def isDestroyingOrEmptyingQueueInProgress: Boolean =
    currentState.isShuttingDown || isEmptyingQueueRunning

  private def isEmptyingQueueRunning: Boolean =
    Option(emptyingQueueFuture.get()).exists(!_.isDone)

  /** Start processing command queue if it is not yet active. */
  private def startQueueProcessing(handler: OSProcessHandler): Unit = {
    PooledThreadExecutor.INSTANCE.submit(new Runnable {
      override def run(): Unit = try {
        // queue ready signal is given by initCommunication.stateChanger
        shellQueueReady.drainPermits()

        //
        // Main loop of commands queue processing
        //
        def stopProcessingCommandsQueue: Boolean = handler.isProcessTerminating || handler.isProcessTerminated || currentState.isShuttingDown
        while (!stopProcessingCommandsQueue) {
          processNextQueuedCommand(1.second)
        }

        // Process terminated, notify remaining commands in the queue
        // otherwise, there might be some stuck processes
        commands.forEach { case QueuedCommand(_, cmd, listener) =>
          Log.warn(s"Sbt shell is terminated, skipping command: $cmd")
          listener.processTerminated()
        }
        commands.clear()

        if (!afterRestartCommands.isEmpty) {
          process.acquireShellProcessHandler()
          moveAccumulatedCommandsToStandardQueue()
        }
        communicationActive.release()
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
        // in the `whenReady` callback parameter of `SbtShellReadyListener` created in `initCommunication`
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
    val QueuedCommand(_, cmd, listener) = qc

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
    listener.future.onComplete { _ =>
      handler.removeProcessListener(listener)
    }
  }

  /**
    * To be called when the process is reinitialized externally.
    * Will only work correctly when `acquireShellProcessHandler.isStartNotify == true`
    * This is usually ensured by calling openShellRunner first, but it's possible
    * to manually trigger it if a fully background process is desired
    */
  private[shell] def initCommunication(handler: OSProcessHandler): Unit = {
    if (communicationActive.tryAcquire(5, TimeUnit.SECONDS)) {
      val releaseCommandQueueListener = new SbtShellReadyListener(
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
    }
  }

  def emitShellStateEvent(event: ShellStateEvent): Unit = {
    val next = SbtShellLifecycle.transition(currentState, event)
    stateRef.set(next)
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
      reporter.startTask(dumpTaskId, None, startMessage)
      messages

    case TaskComplete =>
      reporter.finishTask(dumpTaskId, finishMessage, new SuccessResultImpl())
      val messagesUpdated =
        if (messages.status == BuildMessages.Indeterminate) messages.status(BuildMessages.OK)
        else messages
      messagesUpdated

    case ProcessTerminated =>
      //TODO: it seems like in practice "process terminated" is not used at all
      // we need to refactor the reporter API to not demand it
      reporter.finishTask(dumpTaskId, "process terminated", new SuccessResultImpl())
      messages
        .addError("process terminated")
        .status(BuildMessages.Canceled)

    case ErrorWaitForInput =>
      val msg = SbtBundle.message("sbt.import.errors.project.reload.aborted")
      val ex = new ExternalSystemException(msg)

      val result = new FailureResultImpl(msg, ex)
      reporter.finishTask(dumpTaskId, msg, result)

      messages.addError(msg)

    case Output(raw) =>
      // Strip ANSI codes in both old and new sbt shell modes for simplicity - it's harmless in old mode.
      val text = BuildMessages.stripAnsiCodes(raw).trim

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
  private class InitialErrorDetectorListener extends LineListener with ProjectShellModeProvider(project) {
    private var isReadyState: Boolean = false

    override def onLine(line: String): Unit =
      if (promptReady(line, isNewShell))
        isReadyState = true
      else if (!isReadyState && promptError(line))
        sendIgnore()
  }

  private class CommandListener[A](default: A, aggregator: EventAggregator[A], terminationMessage: Option[String] = None)
    extends LineListener with ProjectShellModeProvider(project) {

    private val promise = Promise[A]()
    private var a: A = default

    private def aggregate(event: ShellEvent): Unit = {
      a = aggregator(a, event)
    }

    def future: Future[A] = promise.future

    def started(): Unit =
      aggregate(TaskStart)

    override def processTerminated(event: ProcessEvent): Unit = {
      processTerminated()
    }

    def processTerminated(): Unit = {
      aggregate(ProcessTerminated)
      val message = terminationMessage.getOrElse("Sbt shell terminated before command is finished")
      promise.complete(Failure(new RuntimeException(message)))
    }

    override def onLine(text: String): Unit =
      if (!promise.isCompleted && promptReady(text, isNewShell)) {
        aggregate(TaskComplete)
        promise.complete(Success(a))
      }
      else if (promptError(text)) {
        // When sbt displays an interactive error prompt, automatically send "i" (ignore) to continue
        sendIgnore()
        aggregate(ErrorWaitForInput)
      } else {
        aggregate(Output(text))
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
    private[SbtShellLifecycle] case object Idle extends ShellState
    /**
     * The shell is alive and has commands pending in the standard command queue (see [[org.jetbrains.sbt.shell.SbtShellCommunication.commands]])
     * or the queue is empty but the last command is still running.
     */
    private[SbtShellLifecycle] case object Queued extends ShellState
    /** The shell is in the process of shutting down, but the process has not terminated yet. */
    private[SbtShellLifecycle] case object ShuttingDown extends ShellState
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
      if (isInternalMode) log.error(msg)
      else log.warn(msg)

      state
    }

    (state, event) match {
      case (Off, QueueDrained)            => Idle
      case (Off, EnqueueCommand)          => Queued
      case (Off, _)                       => logProhibitedTransition()

      case (Idle, EnqueueCommand)         => Queued
      case (Idle, ShutdownRequested)      => ShuttingDown
      case (Idle, QueueDrained)           => Idle // The self-transition Idle -> Idle is allowed for now. It can occur because QueueDrained can be omitted in #initCommunication
                                                  // and then again when the shell becomes ready. TODO add "Start" shell state to get rid of this
      case (Idle, _)                      => logProhibitedTransition()

      case (Queued, QueueDrained)           => Idle
      case (Queued, ShutdownRequested)      => ShuttingDown
      case (Queued, EnqueueCommand)         => Queued  // This occurs when the shell is in the Queued state and another command is added, triggering another EnqueueCommand event.
                                                       // Another scenario for the Queued -> Queued transition is similar to the one described in the Idle -> Idle transition case.
      case (Queued, _)                      => logProhibitedTransition()

      case (ShuttingDown, ProcessTerminated) => Off
      case (ShuttingDown, QueueDrained)      => ShuttingDown // QueueDrained & EnqueueCommand events may still be emitted after shutdown has started,
                                                             // because SbtShellReadyListener#whenReady can fire even when the shell is already in the ShuttingDown state.
      case (ShuttingDown, EnqueueCommand)    => ShuttingDown
      case (ShuttingDown, _)                 => logProhibitedTransition()
    }
  }
}

/**
  * Monitor sbt prompt status, do something when state changes.
  *
  * @param whenReady callback when going into Ready state
  * @param whenWorking callback when going into Working state
  */
private[shell] class SbtShellReadyListener(
  debugName: String,
  whenReady: => Unit,
  whenWorking: => Unit,
  project: Project,
) extends LineListener with ProjectShellModeProvider(project) {

  private var readyState: Boolean = false

  override def toString: String = s"${super.toString} ($debugName)"

  override def onLine(line: String): Unit = {
    val sbtReady: Boolean = promptReady(line, isNewShell) || (readyState && debuggerMessage(line))
    log.traceSafe(f"onLine: (sbtReady: $sbtReady%-5s) $line")

    if (sbtReady && !readyState) {
      readyState = true
      whenReady
    }
    else if (!sbtReady && readyState) {
      readyState = false
      whenWorking
    }
  }
}

private[shell] object SbtProcessUtil {

  /**
   * The prompt marker is inserted by the `sbt-idea-shell plugin`.
   * Should be the same as in `org.jetbrains.sbt.constants.IDEA_PROMPT_MARKER`
   */
  private val IDEA_PROMPT_MARKER = "[IJ]"

  private val DEFAULT_SHELL_PROMPT = "sbt:"

  def promptReady(line: String, withNewShell: Boolean): Boolean =
    if (withNewShell) {
      // When using the new shell (with the built-in shell command), jline3 is utilized under the hood since sbt 1.4.
      // Before displaying any prompt, jline3 prints the BRACKETED_PASTE_ON escape sequence to the terminal to enable bracketed paste mode.
      // If a line contains this escape sequence, it indicates that the line contains a prompt.
      // As a fallback, we check if the line starts with the default shell prompt ("sbt:project_name").
      // This heuristic may fail for users with custom prompts but should work for most standard configurations.
      val bracketedPasteModeEnabled = "\u001B[?2004h"
      val isBracket = line.contains(bracketedPasteModeEnabled)
      isBracket || {
        val lineWithNoAnsi = BuildMessages.stripAnsiCodes(line)
        lineWithNoAnsi.trim.startsWith(DEFAULT_SHELL_PROMPT)
      }
    } else {
      line.trim.startsWith(IDEA_PROMPT_MARKER)
    }

  def promptError(line: String): Boolean =
    line.trim.contains("Project loading failed: (r)etry, (q)uit, (l)ast, or (i)gnore?")

  // sucky workaround for jdwp printing this line on the console when deactivating debugger
  def debuggerMessage(line: String): Boolean =
    line.contains("Listening for transport")

  implicit class StringExt(private val str: String) extends AnyVal {
    def trimRight: String = str.replaceAll("\\s+$", "")
  }
}

private[shell] trait ShellModeProvider {
  /**
   * The lazy evaluation is a workaround to initialize this variable only when the shell process is started and the first line
   * from the shell is being processed.
   * Potentially using this method when the shell is not started may return an incorrect result, i.e., it may return `false`
   * even though the registry is enabled and the shell will be started in the new mode.
   * So be careful and use it only when it's clear that the shell is running.
   */
  protected lazy val isNewShell: Boolean
}

private[shell] trait ProjectShellModeProvider(project: Project) extends ShellModeProvider {
  /**
   * See [[ShellModeProvider.isNewShell]] for details how to use it.
   */
  override protected lazy val isNewShell: Boolean =
    SbtProcessManager.forProject(project).isRunWithNewShell
}

/**
  * Pieces lines back together from parts of colored lines.
  */
abstract class LineListener extends ProcessListener with AnsiEscapeDecoder.ColoredTextAcceptor with ShellModeProvider  {
  protected val log: Logger = Logger.getInstance(getClass)

  def onLine(line: String): Unit

  override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit =
    processCompleteLines(event.getText)

  override def coloredTextAvailable(text: String, attributes: Key[?]): Unit =
    processCompleteLines(text)

  /**
   * Tracks content of the last line until new line character is processed
   */
  private var lastIncompleteLine: String = ""

  /**
   * @param text can start from new line, end with new line, have new line in the middle and no line at all.
   *             Examples: {{{
   *               hello
   *               \nhello
   *               hello\n
   *               hello\r\nworld\r\n
   *               etc ...
   *             }}}
   */
  private def getCompleteLines(text: String): Seq[String] = lastIncompleteLine.synchronized {
    if (log.isTraceEnabled) {
      val textWithEscapedNewLines = escapeNewLines(text)
      log.trace(f"buildLine: $textWithEscapedNewLines")
    }

    val endsWithLineSeparator = text.endsWith("\n") || text.endsWith("\r\n")

    val textWithRemainingLineContent = lastIncompleteLine + text

    //split lines by line separator, "-1" argument is to keep empty lines
    val lines = LineSeparatorRegex.pattern.split(textWithRemainingLineContent, -1).toSeq

    lastIncompleteLine = ""

    if (endsWithLineSeparator) {
      //flush all lines, but drop trailing empty line
      //(it's an empty string, because we used '-1' in 'split' method)
      lines.init
    }
    else {
      val lastLineOption = lines.lastOption
      val shouldFlushLastLine = lastLineOption.exists(line => promptReady(line, isNewShell) || promptError(line))
      if (shouldFlushLastLine) {
        //NOTE: last line with IJ prompt or error might not have new line character in the end
        //But we still want it to be reported the line to detect that the console is "ready"
        lines
      }
      else {
        lastIncompleteLine = lastLineOption.getOrElse("")
        lines.init
      }
    }
  }

  @TestOnly
  @Internal
  def processCompleteLines(text: String): Unit = {
    val lines = getCompleteLines(text)
    lines.foreach(onLine)
  }
}

object LineListener {
  private val LineSeparatorRegex = """\r?\n""".r

  private def escapeNewLines(text: String): String =
    text
      .replace("\\n", "\\\\n").replace("\n", "\\n")
      .replace("\\r", "\\\\r").replace("\r", "\\r")
}
