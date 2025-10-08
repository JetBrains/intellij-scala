package org.jetbrains.sbt.runner

import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, invokeAndWait, invokeLater}
import org.jetbrains.sbt.runner.SbtProgramRunnerBase.{DummyProcessHandler, commandFinishedSuccessfully}
import org.jetbrains.sbt.shell.SbtShellCommunication.{Output, ShellEvent}
import org.jetbrains.sbt.shell.{SbtShellCommunication, SbtShellToolWindowFactory}

import java.io.OutputStream
import scala.concurrent.Future
import scala.util.Try

trait SbtProgramRunnerBase {

  /**
   * @see [[com.intellij.execution.impl.RunConfigurationBeforeRunProvider.doRunTask]]
   */
  protected def delegateExecutionToSbtShell(environment: ExecutionEnvironment, sbtState: SbtCommandLineState): Unit = {
    val project = environment.getProject
    val executorId = environment.getExecutor.getId

    // In order `RunConfigurationBeforeRunProvider.doRunTask` detects that the "Before launch" task is finished, we have to notify the listeners manually.
    // Details:
    // The most standard way to execute is to launch a separate process.
    // This, for example, is what `com.intellij.execution.impl.RunConfigurationBeforeRunProvider.doRunTask` expects
    // It listens for the com.intellij.execution.ExecutionManager.EXECUTION_TOPIC topic,
    // and all the events are later generated in com.intellij.execution.impl.ExecutionManagerImpl.
    // However, when we delegate execution to sbt shell, we don't launch a new process, so we don't have a dedicated process handler for that.
    // Thus, we have to manually notify the listeners.
    // Related: SCL-24434
    val listeners = project.getMessageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC)
    listeners.processStartScheduled(executorId, environment)

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

      val commandFuture = submitCommands(environment, sbtState)
      commandFuture.onComplete { result =>
        // We have to create a dummy handler because `processTerminated` requires it.
        // (Though it's not used in RunConfigurationBeforeRunProvider.doRunTask)
        val dummyProcessHandler = new DummyProcessHandler()
        val exitCode = if (commandFinishedSuccessfully(result)) 0 else 1
        listeners.processTerminated(executorId, environment, dummyProcessHandler, exitCode)
      }
    }): Runnable)
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

    val sbtCommunication = SbtShellCommunication.forProject(project)
    val commands = state.processedCommands

    val listener = state.getListener.getOrElse((_: String) => ())

    // Q: what is this builder needed for anyway? It seems to be ignored
    val eventHandler = (builder: StringBuilder, event: ShellEvent) => {
      event match {
        case Output(line) =>
          listener.apply(line)
          builder.append("\n").append(line)
        case _ =>
          builder
      }
    }

    sbtCommunication.command(commands, new StringBuilder(), eventHandler)
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
    lastLine.exists(SbtShellCommunication.isErrorOutput)
  }
}
