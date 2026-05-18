package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, invokeAndWait, invokeLater}
import org.jetbrains.sbt.runner.SbtProgramRunnerBase.{DummyProcessHandler, HiddenRunContentDescriptor, commandFinishedSuccessfully}
import org.jetbrains.sbt.shell.SbtShellToolWindowFactory
import org.jetbrains.sbt.shell.communication.{SbtShellBuildMessagesEventProcessor, SbtShellCommandEventProcessor, SbtShellCommandRequest, SbtShellCommandSubmitter}

import java.io.OutputStream
import javax.swing.JPanel
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
    // Sbt shell delegation does not start a dedicated OS process, so return a hidden descriptor with a synthetic
    // process handler and let `ExecutionManagerImpl` publish the same start/finish events as for regular runs.
    // Related: SCL-24434, SCL-22453
    val dummyProcessHandler = new DummyProcessHandler()

    // Ensure all the documents are flushed to disk before running "sbt task" run configuration,
    // otherwise, if you make any changes in some document and do e.g. "sbt assembly", sbt won't see the latest changes.
    // Note1: It works fine with the "Build" because that action also commits the document.
    // Note2: This is not needed when sbt shell is not used, because a separate process will be started
    // and documents are saved in `com.intellij.execution.impl.DefaultJavaProgramRunner.doExecute`
    invokeAndWait {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    ApplicationManager.getApplication.executeOnPooledThread((() => {
      import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext

      val commandFuture: Future[CharSequence] =
        try {
          submitCommands(environment, sbtState)
        } catch {
          case exception: Throwable =>
            Future.failed(exception)
        }
      commandFuture.onComplete { result =>
        val exitCode = if (commandFinishedSuccessfully(result)) 0 else 1
        dummyProcessHandler.terminate(exitCode)
      }
    }): Runnable)

    new HiddenRunContentDescriptor(dummyProcessHandler, environment.getRunProfile.getName)
  }

  /**
   * @return a future with all the output collected during the command execution
   */
  @RequiresBackgroundThread
  protected def submitCommands(
    env: ExecutionEnvironment,
    state: SbtCommandLineState,
  ): Future[java.lang.CharSequence] = {
    val project = env.getProject

    // When running sbt run configuration show sbt shell if it's hidden
    invokeLater {
      showSbtToolwindow(project)
    }

    val sbtCommunication = SbtShellCommandSubmitter.instance(project)
    val commands = state.processedCommands

    val listener = state.getListener.getOrElse((_: String) => ())

    val eventProcessor: SbtShellCommandEventProcessor[StringBuilder] = new SbtShellCommandEventProcessor.OutputCollector().tap(
      new SbtShellCommandEventProcessor.OutputLineListener(listener)
    )

    val request = SbtShellCommandRequest(commands, eventProcessor)
    sbtCommunication.run(request)
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
  private class DummyProcessHandler extends ProcessHandler {
    def terminate(exitCode: Int): Unit = notifyProcessTerminated(exitCode)

    override def destroyProcessImpl(): Unit = ()

    override def detachProcessImpl(): Unit = ()

    override def detachIsDefault(): Boolean = false

    @Nullable override def getProcessInput: OutputStream = null
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

  private class HiddenRunContentDescriptor(processHandler: ProcessHandler, displayName: String)
    extends RunContentDescriptor(null, processHandler, new JPanel(), displayName) {

    override def isHiddenContent: Boolean = true
  }
}
