package org.jetbrains.sbt.shell

import java.io.{OutputStreamWriter, PrintWriter}
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.execution.process.{AnsiEscapeDecoder, OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTaskResult
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.sbt.shell.SbtProcessUtil._

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


  // TODO ask sbt to provide completions for a line via its parsers
  def completion(line: String): List[String] = List.empty

  /**
    * Execute an sbt task.
    */
  def command(cmd: String): Future[ProjectTaskResult] =
    queueCommand(cmd, new CommandListener(None))

  def commandWithIndicator(cmd: String, indicator: ProgressIndicator): Future[ProjectTaskResult] = {
    val listener = new CommandListener(Option(indicator))
    queueCommand(cmd, listener)
  }

  private def queueCommand(cmd: String, listener: CommandListener) = {

    initCommunication(process.acquireShellProcessHandler)

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

        // TODO more robust way to get the writer? cf createOutputStreamWriter
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
  def initCommunication(handler: OSProcessHandler): Unit = {
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
}

class CommandListener(indicator: Option[ProgressIndicator]) extends LineListener {

  private var success = false
  private var errors = 0
  private var warnings = 0

  private val promise = Promise[ProjectTaskResult]()

  def future: Future[ProjectTaskResult] = promise.future

  override def startNotified(event: ProcessEvent): Unit = {
    indicator.foreach { i =>
      i.setText("build started")
      i.setFraction(0.1)
    }
  }

  override def processTerminated(event: ProcessEvent): Unit = {
    val res = new ProjectTaskResult(true, errors, warnings)
    indicator.foreach(_.stop())
    promise.complete(Success(res))
  }

  override def onLine(text: String): Unit = {

    indicator.foreach { i =>
      i.setFraction(0.2)
      i.setText("building ...")
      i.setText2(text)
    }

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
      indicator.foreach { i =>
        i.setFraction(1)
        i.setText("build completed")
        i.stop()
      }
      promise.complete(Success(res))
    }
  }

}


/**
  * Monitor sbt prompt status, do something when state changes.
  * TODO: Maybe specify an internal callback thingy just for ready state changes so that other parts don't have to depend on prompt changes
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

object SbtProcessUtil {

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
abstract class LineListener extends ProcessAdapter with AnsiEscapeDecoder.ColoredTextAcceptor {

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
