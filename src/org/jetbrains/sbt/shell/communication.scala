package org.jetbrains.sbt.shell

import java.io.{OutputStreamWriter, PrintWriter}
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.execution.process.{AnsiEscapeDecoder, OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTaskResult
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.sbt.shell.SbtProcessUtil._
import org.jetbrains.sbt.shell.SbtShellCommunication._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationLong}
import scala.concurrent.{Future, Promise}
import scala.util.Success

/**
  * Created by jast on 2016-11-06.
  */
class SbtShellCommunication(project: Project) extends AbstractProjectComponent(project) {

  private lazy val process = SbtProcessManager.forProject(project)

  private val shellPromptReady = new AtomicBoolean(false)
  private val communicationActive = new AtomicBoolean(false)
  private val shellQueueReady = new Semaphore(1)
  private val commands = new LinkedBlockingQueue[(String, CommandListener)]()

  /**
    * Queue an sbt command for execution in the sbt shell.
    */
  def command(cmd: String, eventHandler: EventHandler = _=>(), showShell: Boolean = true): Future[ProjectTaskResult] = {
    val eventHandler: EventHandler = _ => ()
    val listener = new CommandListener(eventHandler)
    if (showShell) process.openShellRunner()
    initCommunication(process.acquireShellProcessHandler)
    queueCommand(cmd, listener)
  }

  private def queueCommand(cmd: String, listener: CommandListener) = {

    commands.put((cmd, listener))

    listener.future.recover {
      case _ =>
        // TODO some kind of feedback / rethrow
        new ProjectTaskResult(true, 1, 0)
    }
  }

  def attachListener(listener: ProcessAdapter): Unit = {
    val handler = process.acquireShellProcessHandler
    handler.addProcessListener(listener)
  }

  /** Start processing command queue if it is not yet active. */
  private def startQueueProcessing(handler: OSProcessHandler): Unit = {

    // is it ok for this executor to run a queue processor?
    PooledThreadExecutor.INSTANCE.submit(new Runnable {
      override def run(): Unit = {
        // make sure there is exactly one permit available
        shellQueueReady.drainPermits()
        shellQueueReady.release()
        while (!handler.isProcessTerminating && !handler.isProcessTerminated) {
          nextQueuedCommand(1.second)
        }
        communicationActive.set(false)
      }
    })
  }

  private def nextQueuedCommand(timeout: Duration) = {
    // TODO exception handling
    if (shellQueueReady.tryAcquire(timeout.toMillis, TimeUnit.MILLISECONDS)) {
      val next = commands.poll(timeout.toMillis, TimeUnit.MILLISECONDS)
      if (next != null) {
        val (cmd, listener) = next

        val handler = process.acquireShellProcessHandler
        handler.addProcessListener(listener)

        val shell = new PrintWriter(new OutputStreamWriter(handler.getProcessInput))

        // we want to avoid registering multiple callbacks to the same output and having whatever side effects
        shell.println(cmd)
        shell.flush()

        listener.future.onComplete { _ =>
          handler.removeProcessListener(listener)
          shellQueueReady.release()
        }
      } else shellQueueReady.release()
    }
  }

  /**
    * To be called when the process is reinitialized externally
    */
  private[shell] def initCommunication(handler: OSProcessHandler): Unit = {
    if (!communicationActive.getAndSet(true)) {
      val stateChanger = new SbtShellReadyListener(
        whenReady = shellPromptReady.set(true),
        whenWorking = shellPromptReady.set(false)
      )

      shellPromptReady.set(false)
      handler.addProcessListener(stateChanger)
      if (!handler.isStartNotified) handler.startNotify()

      startQueueProcessing(handler)
    }
  }
}

object SbtShellCommunication {
  def forProject(project: Project): SbtShellCommunication = project.getComponent(classOf[SbtShellCommunication])

  type EventHandler = ShellEvent => Unit

  sealed trait ShellEvent
  case object TaskStart extends ShellEvent
  case object TaskComplete extends ShellEvent
  case class Output(line: String) extends ShellEvent
}


private[shell] class CommandListener(eventHandler: EventHandler) extends LineListener {

  private var success = false
  private var errors = 0
  private var warnings = 0

  private val promise = Promise[ProjectTaskResult]()

  def future: Future[ProjectTaskResult] = promise.future

  override def startNotified(event: ProcessEvent): Unit = eventHandler(TaskStart)

  override def processTerminated(event: ProcessEvent): Unit = {
    val res = new ProjectTaskResult(true, errors, warnings)
    // TODO separate event type for completion by termination?
    eventHandler(TaskComplete)
    promise.complete(Success(res))
  }

  override def onLine(text: String): Unit = {

    if (text startsWith "[error]") {
      success = false
      errors += 1
    } else if (text startsWith "[warning]") {
      warnings += 1
    }
    else if (text contains "[success]")
      success = true
      // TODO running multiple tasks at once will output multiple success lines, so this may negate previous errors

    if (!promise.isCompleted && promptReady(text)) {
      val res = new ProjectTaskResult(false, errors, warnings)

      eventHandler(TaskComplete)
      promise.complete(Success(res))
    } else {
      eventHandler(Output(text))
    }
  }
}


/**
  * Monitor sbt prompt status, do something when state changes.
  *
  * @param whenReady callback when going into Ready state
  * @param whenWorking callback when going into Working state
  */
class SbtShellReadyListener(whenReady: =>Unit, whenWorking: =>Unit) extends LineListener {

  private var readyState: Boolean = false

  def onLine(line: String): Unit = {
    val sbtReady = promptReady(line)
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

  def promptReady(line: String): Boolean =
    line.trim match {
      case
        ">" | // todo can we guard against false positives? like somebody outputting > on the bare prompt
        "scala>" |
        "Hit enter to retry or 'exit' to quit:"
        => true

      case _ => false
    }
}

/**
  * Pieces lines back together from parts of colored lines.
  */
private[shell] abstract class LineListener extends ProcessAdapter with AnsiEscapeDecoder.ColoredTextAcceptor {

  private val builder = new StringBuilder

  def onLine(line: String): Unit

  override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit =
    updateLine(event.getText)

  override def coloredTextAvailable(text: String, attributes: Key[_]): Unit =
    updateLine(text)

  private def updateLine(text: String) = {
    text match {
      case "\n" =>
        lineDone()
      case t if t.endsWith("\n") =>
        builder.append(t.dropRight(1))
        lineDone()
      case t =>
        builder.append(t)
        val lineSoFar = builder.result()
        if (promptReady(lineSoFar)) lineDone()
    }
  }

  private def lineDone() = {
    val line = builder.result()
    builder.clear()
    onLine(line)
  }
}
