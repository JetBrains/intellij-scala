package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.{ExecutionEnvironment, RunContentBuilder}
import com.intellij.execution.ui.{ConsoleView, RunContentDescriptor}
import com.intellij.execution.DefaultExecutionResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, invokeAndWait, invokeLater}
import org.jetbrains.sbt.runner.SbtProgramRunnerBase.{DummyProcessHandler, commandFinishedSuccessfully}
import org.jetbrains.sbt.runner.console.SbtShellWaitingForReadyHint
import org.jetbrains.sbt.shell.SbtShellToolWindowFactory
import org.jetbrains.sbt.shell.communication.{SbtShellBuildMessagesEventProcessor, SbtShellCommandEventProcessor, SbtShellCommandRequest, SbtShellCommandRequestId, SbtShellCommandSubmitter}

import java.io.OutputStream
import scala.concurrent.Future
import scala.util.Try

trait SbtProgramRunnerBase {

  protected def isDebugExecutorId(executorId: String): Boolean =
    executorId == DefaultDebugExecutor.EXECUTOR_ID

  /**
   * @see [[com.intellij.execution.impl.RunConfigurationBeforeRunProvider.doRunTask]]
   */
  protected def delegateExecutionToSbtShell(
    environment: ExecutionEnvironment,
    sbtState: SbtCommandLineState
  ): RunContentDescriptor = {
    // Some before-run tasks observe the run configuration lifecycle through ExecutionManager.EXECUTION_TOPIC.
    // E.g., see `RunConfigurationBeforeRunProvider.doRunTask` and `LaunchBrowserBeforeRunTaskProvider.executeTask`.
    // Sbt shell delegation does not start a dedicated OS process, so return a descriptor with a synthetic
    // process handler and let `ExecutionManagerImpl` publish the same start/finish events as for regular runs.
    // Related: SCL-24434, SCL-22453
    val sbtCommandSubmitter = SbtShellCommandSubmitter.instance(environment.getProject)
    val request = shellCommandRequest(sbtState.processedCommands)
    val dummyProcessHandler = new DummyProcessHandler(sbtCommandSubmitter, request.requestId)

    // Ensure all the documents are flushed to disk before running "sbt task" run configuration,
    // otherwise, if you make any changes in some document and do e.g. "sbt assembly", sbt won't see the latest changes.
    // Note1: It works fine with the "Build" because that action also commits the document.
    // Note2: This is not needed when sbt shell is not used, because a separate process will be started
    // and documents are saved in `com.intellij.execution.impl.DefaultJavaProgramRunner.doExecute`
    invokeAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // Attach the run console before submitting the shell command; the new shell can print and finish very quickly.
    val (runContentDescriptor, consoleView) = createRunContentDescriptor(environment, dummyProcessHandler)
    val requestWithRunContentOutput = request
      .withProcessorModified(_.tap(new RunContentConsoleOutputProcessor(dummyProcessHandler)))
      .withQueuedWhileShellBusyNotification(() => {
        SbtShellWaitingForReadyHint.print(consoleView)
      })
      .withSbtShellToolWindowActivationOnStartup(enabled = false)

    ApplicationManager.getApplication.executeOnPooledThread((() => {
      import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext

      val commandFuture: Future[CharSequence] =
        try {
          if (dummyProcessHandler.isCancellationRequested) {
            Future.failed(new ProcessCanceledException)
          } else {
            val future = submitCommandsToShell(environment, requestWithRunContentOutput, sbtCommandSubmitter, showSbtToolWindow = false)
            dummyProcessHandler.commandSubmitted()
            future
          }
        } catch {
          case exception: Throwable =>
            Future.failed(exception)
        }
      commandFuture.onComplete { result =>
        val exitCode = if (commandFinishedSuccessfully(result)) 0 else 1
        dummyProcessHandler.terminate(exitCode)
      }
    }): Runnable)

    runContentDescriptor
  }

  /**
   * @return a future with all the output collected during the command execution
   */
  @RequiresBackgroundThread
  protected def submitCommandsToShell(
    env: ExecutionEnvironment,
    sbtCommands: String,
    commandOutputProcessHandler: Option[ProcessHandler] = None,
    onQueuedWhileShellBusy: () => Unit = () => (),
  ): Future[java.lang.CharSequence] = {
    val sbtCommandSubmitter = SbtShellCommandSubmitter.instance(env.getProject)
    val request = {
      val baseRequest = shellCommandRequest(sbtCommands, commandOutputProcessHandler)
      val requestWithNotification = baseRequest.withQueuedWhileShellBusyNotification(onQueuedWhileShellBusy)
      if (commandOutputProcessHandler.isEmpty) requestWithNotification
      else requestWithNotification.withSbtShellToolWindowActivationOnStartup(enabled = false)
    }
    submitCommandsToShell(
      env,
      request,
      sbtCommandSubmitter,
      // Show sbt tool window only if we can't show the Run Configuration console
      showSbtToolWindow = commandOutputProcessHandler.isEmpty
    )
  }

  private def shellCommandRequest(
    sbtCommands: String,
    commandOutputProcessHandler: Option[ProcessHandler] = None,
  ): SbtShellCommandRequest[StringBuilder] = {
    val outputCollector = new SbtShellCommandEventProcessor.OutputCollector()
    val eventProcessor: SbtShellCommandEventProcessor[StringBuilder] =
      commandOutputProcessHandler.fold(outputCollector: SbtShellCommandEventProcessor[StringBuilder]) { processHandler =>
        outputCollector.tap(new RunContentConsoleOutputProcessor(processHandler))
      }
    SbtShellCommandRequest(sbtCommands, eventProcessor)
  }

  @RequiresBackgroundThread
  private def submitCommandsToShell(
    env: ExecutionEnvironment,
    request: SbtShellCommandRequest[StringBuilder],
    sbtCommunication: SbtShellCommandSubmitter,
    showSbtToolWindow: Boolean,
  ): Future[java.lang.CharSequence] = {
    val project = env.getProject

    if (showSbtToolWindow) {
      // When there is no dedicated run content console, show sbt shell if it's hidden.
      invokeLater {
        showSbtToolwindow(project)
      }
    }

    sbtCommunication.run(request)
  }

  private def createRunContentDescriptor(
    environment: ExecutionEnvironment,
    processHandler: ProcessHandler,
  ): (RunContentDescriptor, ConsoleView) = {
    val consoleView = new ConsoleViewImpl(environment.getProject, false)
    consoleView.attachToProcess(processHandler)

    val executionResult = new DefaultExecutionResult(consoleView, processHandler)
    (new RunContentBuilder(executionResult, environment).showRunContent(environment.getContentToReuse), consoleView)
  }

  protected def isSbtRunConfigurationWithUseSbtShell(profile: RunProfile): Boolean = profile match {
    case sbtConf: SbtRunConfiguration =>
      sbtConf.useSbtShell
    case _ =>
      false
  }

  private def showSbtToolwindow(project: Project): Unit = {
    val toolwindow = ToolWindowManager.getInstance(project).getToolWindow(SbtShellToolWindowFactory.ID)
    if (toolwindow != null) {
      toolwindow.show()
    }
  }
}

object SbtProgramRunnerBase {
  private class DummyProcessHandler(
    sbtCommandSubmitter: SbtShellCommandSubmitter,
    requestId: SbtShellCommandRequestId,
  ) extends ProcessHandler {
    private var submitted: Boolean = false
    private var cancellationRequested: Boolean = false
    private var cancellationSent: Boolean = false
    private var finished: Boolean = false

    def terminate(exitCode: Int): Unit = {
      synchronized {
        finished = true
      }
      notifyProcessTerminated(exitCode)
    }

    def commandSubmitted(): Unit = {
      synchronized {
        submitted = true
      }
      cancelSubmittedCommandIfNeeded()
    }

    def isCancellationRequested: Boolean =
      synchronized {
        cancellationRequested
      }

    override def destroyProcessImpl(): Unit = {
      synchronized {
        cancellationRequested = true
      }
      cancelSubmittedCommandIfNeeded()
    }

    override def detachProcessImpl(): Unit =
      destroyProcessImpl()

    override def detachIsDefault(): Boolean = false

    @Nullable override def getProcessInput: OutputStream = null

    private def cancelSubmittedCommandIfNeeded(): Unit = {
      val shouldCancel = synchronized {
        val shouldCancel = submitted && cancellationRequested && !cancellationSent && !finished
        if (shouldCancel) {
          cancellationSent = true
        }
        shouldCancel
      }

      if (shouldCancel) {
        sbtCommandSubmitter.cancel(requestId)
      }
    }
  }

  private def commandFinishedSuccessfully(result: Try[CharSequence]): Boolean = {
    // ATTENTION: technically it's not the most correct and reliable way to detect if a command was finished "successfully".
    // But it's the only thing we can do now, with the text-based sbt shell integration
    result.toOption.exists(output => !endsWithErrorOutput(output.toString))
  }

  private def endsWithErrorOutput(output: String): Boolean = {
    val lastLine = output.trim.linesIterator.lastOption
    lastLine.exists(SbtShellBuildMessagesEventProcessor.isErrorOutput)
  }
}
