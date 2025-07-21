package org.jetbrains.sbt.shell

import com.intellij.execution.process.{AnsiEscapeDecoder, OSProcessHandler, ProcessEvent, ProcessListener}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.{NonNls, TestOnly}
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.sbt.shell.LineListener.{LineSeparatorRegex, escapeNewLines}
import org.jetbrains.sbt.shell.SbtProcessUtil._
import org.jetbrains.sbt.shell.SbtShellCommunication._

import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationLong}
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}

/**
  * Service for connecting with an sbt shell associated with project.
  */
@Service(Array(Service.Level.PROJECT))
final class SbtShellCommunication(project: Project) {

  private lazy val process: SbtProcessManager = SbtProcessManager.forProject(project)

  private val communicationActive = new Semaphore(1)
  private val shellQueueReady = new Semaphore(1)
  /**
   * An indicator to check if the sbt shell process is being destroyed.
   * It prevents new commands from being added to the queue and processing new commands in the queue while the process is being terminated.
   *
   * @see [[https://youtrack.jetbrains.com/issue/SCL-24121/The-sbt-shell-destroying-method-doesnt-wait-for-the-commands-processing-loop-to-exit]]
   */
  private val isDestroying = new AtomicBoolean(false)
  private val commands = new LinkedBlockingQueue[(String, CommandListener[_])]()
  /**
   * The queue for commands accumulated when the shell is in the process of emptying standard queue commands before the "soft" restart or destroying.
   * Currently, the concept of a "soft" restart is used only when, during a project reload, it's discovered that the sbt version has changed.
   * In such cases, the shell is restarted in a "soft" way - only after processing all already queued commands.
   *
   * If, during emptying the queue, the sbt shell is manually killed by the user or via the dispose method,
   * all commands accumulated in this queue are moved to the standard [[commands]] queue
   * and are terminated in queue processing method ([[startQueueProcessing]]).
   */
  private val afterRestartCommands = new LinkedBlockingQueue[(String, CommandListener[_])]()

  /**
   * Contains an atomic reference to a `Future` responsible for emptying [[commands]] queue.
   */
  private val emptyingQueueFuture = new AtomicReference[CompletableFuture[Unit]](null)

  /** Queue an sbt command for execution in the sbt shell, returning a Future[String] containing the entire shell output. */
  def command(cmd: String): Future[String] =
    command(cmd, new StringBuilder(), messageAggregator).map(_.toString())

  /** Queue an sbt command for execution in the sbt shell. */
  def command[A](@NonNls cmd: String, default: A, eventHandler: EventAggregator[A]): Future[A] = {
    val listener = new CommandListener(default, eventHandler)
    if (isDestroyingOrEmptying) {
      afterRestartCommands.put((cmd, listener))
    } else {
      process.acquireShellRunner()
      commands.put((cmd, listener))
    }

    listener.future
  }

  private def isEmptyingQueueRunning: Boolean =
    Option(emptyingQueueFuture.get()).exists(!_.isDone)

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

  def sendSigInt(): Unit = process.sendSigInt()

  /**
    * Send string directly to the shell without regarding the shell state.
    * This should only be used to send keypresses such as ctrl+c
    */
  def send(keys: String): Unit =
    process.usingWriter { shell =>
      shell.print(keys)
      shell.flush()
    }

  def startDestroying(): Unit = isDestroying.set(true)

  private def finishDestroying(): Unit = isDestroying.set(false)

  private def isDestroyingOrEmptying: Boolean = isDestroying.get() || isEmptyingQueueRunning

  private def isDestroyingInProgress: Boolean = isDestroying.get()

  /** Start processing command queue if it is not yet active. */
  private def startQueueProcessing(handler: OSProcessHandler): Unit = {
    PooledThreadExecutor.INSTANCE.submit(new Runnable {
      override def run(): Unit = try {
        // queue ready signal is given by initCommunication.stateChanger
        shellQueueReady.drainPermits()
        while (!handler.isProcessTerminating && !handler.isProcessTerminated) {
          processNextQueuedCommand(1.second)
        }

        //process terminated, notify remaining commands in the queue
        //otherwise, there might be some stuck processes
        commands.forEach { case (command, listener) =>
          Log.warn(s"Sbt shell is terminated, skipping command: $command")
          listener.processTerminated()
        }
        commands.clear()

        finishDestroying()
        if (!afterRestartCommands.isEmpty) {
          process.acquireShellRunner()
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

  private def processNextQueuedCommand(timeout: Duration): Unit = {
    def tryProcessCommand(): Boolean = {
      if (isDestroyingInProgress) return false

      commands.poll(timeout.toMillis, TimeUnit.MILLISECONDS) match {
        case null => false
        case next =>
          //NOTE: shellQueueReady is released in `SbtShellReadyListener` created in `initCommunication`
        processCommand(next)
          true
      }
    }
    if (commands.isEmpty) return

    // TODO exception handling
    if (!shellQueueReady.tryAcquire(timeout.toMillis, TimeUnit.MILLISECONDS)) return

    val isCommandProcessed = tryProcessCommand()
    if (!isCommandProcessed) {
      shellQueueReady.release()
    }
  }

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
  def commandAfterSoftRestart[A](cmd: String, default: A, eventHandler: EventAggregator[A]): Future[A] = {
    if (isEmptyingQueueRunning) return command(cmd, default, eventHandler)

    val emptyingQueue = new CompletableFuture[Unit]()

    def waitForEmptyQueue(): Unit = {
      val isQueueBusy = () => !commands.isEmpty || shellQueueReady.availablePermits() == 0
      while (isQueueBusy() && !emptyingQueue.isDone) {
        Thread.sleep(1000)
      }
    }

    emptyingQueueFuture.set(emptyingQueue)
    // The command is put on the `afterRestartCommands` queue
    val commandResult = command(cmd, default, eventHandler)
    try {
      waitForEmptyQueue()
      if (!emptyingQueue.isCompletedExceptionally) {
        emptyingQueue.complete(())
        SbtProcessManager.forProject(project).destroyProcess(isSoft = true)
      }
      commandResult
    } finally {
      emptyingQueueFuture.set(null)
    }
  }

  private def processCommand(commandAndListener: (String, CommandListener[_])): Unit = {
    val (cmd, listener) = commandAndListener

    listener.started()

    val handler = process.acquireShellProcessHandler()
    handler.addProcessListener(listener)

    process.usingWriter { shell =>
      shell.print(cmd)
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
        whenReady = shellQueueReady.release(),
        whenWorking = (),
      )
      handler.addProcessListener(releaseCommandQueueListener)
      startQueueProcessing(handler)
    }
  }
}

object SbtShellCommunication {
  protected val Log: Logger = Logger.getInstance(getClass)

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
}

private[shell] class CommandListener[A](default: A, aggregator: EventAggregator[A]) extends LineListener {

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
    promise.complete(Try(a))
  }

  override def onLine(text: String): Unit =
    if (!promise.isCompleted && promptReady(text)) {
      aggregate(TaskComplete)
      promise.complete(Success(a))
    }
    else if (promptError(text))
      aggregate(ErrorWaitForInput)
    else
      aggregate(Output(text))
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
) extends LineListener {

  private var readyState: Boolean = false

  override def toString: String = s"${super.toString} ($debugName)"

  override def onLine(line: String): Unit = {
    val sbtReady: Boolean = promptReady(line) || (readyState && debuggerMessage(line))
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

  // Should be the same as in `org.jetbrains.sbt.constants.IDEA_PROMPT_MARKER`
  private val IDEA_PROMPT_MARKER = "[IJ]"

  // the prompt marker is inserted by the sbt-idea-shell plugin
  def promptReady(line: String): Boolean =
    line.trim.startsWith(IDEA_PROMPT_MARKER)

  def promptError(line: String): Boolean =
    line.trim.endsWith("(r)etry, (q)uit, (l)ast, or (i)gnore?")

  // sucky workaround for jdwp printing this line on the console when deactivating debugger
  def debuggerMessage(line: String): Boolean =
    line.contains("Listening for transport")

  implicit class StringExt(private val str: String) extends AnyVal {
    def trimRight: String = str.replaceAll("\\s+$", "")
  }
}


/**
  * Pieces lines back together from parts of colored lines.
  */
abstract class LineListener extends ProcessListener with AnsiEscapeDecoder.ColoredTextAcceptor {
  protected val log: Logger = Logger.getInstance(getClass)

  def onLine(line: String): Unit

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit =
    processCompleteLines(event.getText)

  override def coloredTextAvailable(text: String, attributes: Key[_]): Unit =
    processCompleteLines(text)

  /**
   * Tracks content of the last line until new line character is processed
   */
  private[this] var lastIncompleteLine: String = ""

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
      val shouldFlushLastLine = lastLineOption.exists(line => promptReady(line) || promptError(line))
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
