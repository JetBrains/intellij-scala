package org.jetbrains.sbt.shell

import java.util.concurrent._

import com.intellij.execution.process.{AnsiEscapeDecoder, OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.sbt.shell.SbtProcessUtil._
import org.jetbrains.sbt.shell.SbtShellCommunication._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationLong}
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}

/**
  * Created by jast on 2016-11-06.
  */
class SbtShellCommunication(project: Project) extends ProjectComponent {

  private lazy val process = SbtProcessManager.forProject(project)

  private val communicationActive = new Semaphore(1)
  private val shellQueueReady = new Semaphore(1)
  private val commands = new LinkedBlockingQueue[(String, CommandListener[_])]()

  /** Queue an sbt command for execution in the sbt shell, returning a Future[String] containing the entire shell output. */
  def command(cmd: String, showShell: Boolean = true): Future[String] =
    command(cmd, StringBuilder.newBuilder, messageAggregator, showShell).map(_.toString())

  /** Queue an sbt command for execution in the sbt shell. */
  def command[A](cmd: String,
                 default: A,
                 eventHandler: EventAggregator[A],
                 showShell: Boolean): Future[A] = {
    val listener = new CommandListener(default, eventHandler)
    process.acquireShellRunner()
    commands.put((cmd, listener))
    listener.future
  }

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

  /** Start processing command queue if it is not yet active. */
  private def startQueueProcessing(handler: OSProcessHandler): Unit = {

    PooledThreadExecutor.INSTANCE.submit(new Runnable {
      override def run(): Unit = {
        // queue ready signal is given by initCommunication.stateChanger
        shellQueueReady.drainPermits()
        while (!handler.isProcessTerminating && !handler.isProcessTerminated) {
          nextQueuedCommand(1.second)
        }
        communicationActive.release()
      }
    })
  }

  private def nextQueuedCommand(timeout: Duration): Unit = {
    // TODO exception handling
    val acquired = shellQueueReady.tryAcquire(timeout.toMillis, TimeUnit.MILLISECONDS)
    if (acquired) {
      val next = commands.poll(timeout.toMillis, TimeUnit.MILLISECONDS)
      if (next != null) {
        val (cmd, listener) = next

        listener.started()

        val handler = process.acquireShellProcessHandler()
        handler.addProcessListener(listener)

        process.usingWriter { shell =>
          shell.println(cmd)
          shell.flush()
        }
        listener.future.onComplete { _ =>
          handler.removeProcessListener(listener)
        }
      } else {
        shellQueueReady.release()
      }
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
      val stateChanger = new SbtShellReadyListener(
        whenReady = shellQueueReady.release(),
        whenWorking = ()
      )

      handler.addProcessListener(stateChanger)

      startQueueProcessing(handler)
    }
  }
}

object SbtShellCommunication {
  def forProject(project: Project): SbtShellCommunication = project.getComponent(classOf[SbtShellCommunication])

  sealed trait ShellEvent
  case object TaskStart extends ShellEvent
  case object TaskComplete extends ShellEvent
  case object ErrorWaitForInput extends ShellEvent
  case class Output(line: String) extends ShellEvent

  sealed trait ErrorReaction
  case object Quit extends ErrorReaction
  case object Ignore extends ErrorReaction

  type EventAggregator[A] = (A, ShellEvent) => A

  /** Aggregates the output of a shell task. */
  val messageAggregator: EventAggregator[StringBuilder] = (builder, e) => e match {
    case TaskStart | TaskComplete | ErrorWaitForInput => builder
    case Output(text) => builder.append("\n").append(text)
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
    // TODO separate event type for completion by termination?
    aggregate(TaskComplete)
    promise.complete(Try(a))
  }

  override def onLine(text: String): Unit = {

    if (!promise.isCompleted && promptReady(text)) {
      aggregate(TaskComplete)
      promise.complete(Success(a))
    } else if (promptError(text)) {
      aggregate(ErrorWaitForInput)
    } else {
      aggregate(Output(text))
    }
  }
}


/**
  * Monitor sbt prompt status, do something when state changes.
  *
  * @param whenReady callback when going into Ready state
  * @param whenWorking callback when going into Working state
  */
class SbtShellReadyListener(whenReady: => Unit, whenWorking: => Unit) extends LineListener {

  private var readyState: Boolean = false

  override def onLine(line: String): Unit = {
    val sbtReady = promptReady(line) || (readyState && debuggerMessage(line))

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

  val IDEA_PROMPT_MARKER = "[IJ]"

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
private[shell] abstract class LineListener extends ProcessAdapter with AnsiEscapeDecoder.ColoredTextAcceptor {

  def onLine(line: String): Unit

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit =
    updateLine(event.getText)

  override def coloredTextAvailable(text: String, attributes: Key[_]): Unit =
    updateLine(text)

  private[this] val builder = new StringBuilder

  private def buildLine(text: String): Option[String] = builder.synchronized {
    def lineDone(): Option[String] = {
      val line = builder.result().trimRight
      builder.clear()
      Some(line)
    }

    text match {
      case "\n" =>
        lineDone()
      case t if t.endsWith("\n") =>
        builder.append(t.dropRight(1))
        lineDone()
      case t =>
        builder.append(t)
        val lineSoFar = builder.result()

        if (promptReady(lineSoFar) || promptError(lineSoFar))
          lineDone()
        else None
    }
  }

  private def updateLine(text: String): Unit = {
    val ready = buildLine(text)
    ready.foreach(onLine)
  }
}
