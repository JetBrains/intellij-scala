package org.jetbrains.sbt.shell.communication

import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.LoggerExt
import org.jetbrains.sbt.shell.communication.ShellEvent.*

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
 * Listens to process output for one command submitted to the sbt shell.
 *
 * The sbt shell integration detects command progress from text output and process lifecycle callbacks.<br>
 * This listener translates those signals into [[ShellEvent]] values, feeds them to the command's
 * [[SbtShellCommandEventProcessor]], and completes [[future]] when the shell prompt indicates that the command is done.
 *
 * @param project project whose sbt shell mode is used to recognize prompt output.
 * @param request the submitted command request that provides the shell event processor and termination message.
 * @tparam Result command result type produced by the processor from [[request]].
 */
private[shell] class SbtShellCommandExecutionOutputListener[Result](
  project: Project,
  request: SbtShellCommandRequest[Result]
) extends SbtOutputCompleteLinesProcessListener(project) {

  private val promise = Promise[Result]()
  private var currentResult: Result = request.shellEventProcessor.initialResult

  private def aggregate(event: ShellEvent): Unit = synchronized {
    currentResult = request.shellEventProcessor.process(currentResult, event)
  }

  private def currentResultSnapshot: Result = synchronized {
    currentResult
  }

  def future: Future[Result] = promise.future

  def started(): Unit = {
    this.log.debug("CommandListener.started")
    aggregate(TaskStart)
  }

  def processQueuedOutput(line: String): Unit =
    aggregate(Output(line))

  override def processTerminated(event: ProcessEvent): Unit = {
    this.log.debug(s"CommandListener.processTerminated(exitCode=${event.getExitCode}, text=${event.getText})")
    processTerminated()
  }

  def processTerminated(): Unit = {
    this.log.debug("CommandListener.processTerminated")
    aggregate(ProcessTerminated)

    val message = request.interruptionErrorMessage.getOrElse("Sbt shell terminated before command is finished")
    promise.complete(Failure(new RuntimeException(message)))
  }

  override def onLine(text: String): Unit = {
    val shellEvent = shellEventForLine(text)
    aggregate(shellEvent)

    if (shellEvent == TaskComplete) {
      promise.complete(Success(currentResultSnapshot))
    }
  }

  private def shellEventForLine(text: String): ShellEvent = {
    val shouldCompleteTask = !promise.isCompleted && SbtShellOutputRecognizer.isPromptReady(text, isNewSbtShell)
    if (shouldCompleteTask) {
      log.traceSafe("CommandListener.onLine: isPromptReady -> TaskComplete")
      TaskComplete
    } else if (SbtShellOutputRecognizer.isProjectLoadingPromptError(text)) {
      log.traceSafe("CommandListener.onLine: isProjectLoadingPromptError detected -> ErrorWaitForInput")
      ErrorWaitForInput
    } else {
      log.traceSafe(s"CommandListener.onLine: output line: $text")
      Output(text)
    }
  }
}
