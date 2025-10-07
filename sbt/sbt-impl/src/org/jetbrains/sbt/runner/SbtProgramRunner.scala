package org.jetbrains.sbt.runner

import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.{RunProfile, RunProfileState, RunnerSettings}
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.{ExecutionEnvironment, GenericProgramRunner, ProgramRunner}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, executionContext, invokeAndWait}
import org.jetbrains.sbt.runner.SbtProgramRunner.DummyProcessHandler
import org.jetbrains.sbt.shell.SbtShellCommunication

import java.io.OutputStream
import scala.util.Try

/**
 * @see [[org.jetbrains.sbt.runner.SbtDebugProgramRunner]]
 */
class SbtProgramRunner extends GenericProgramRunner[RunnerSettings] with SbtProgramRunnerBase {

  override def getRunnerId: String = "SbtProgramRunner"

  override def canRun(executorId: String, profile: RunProfile): Boolean =
    isSbtRunConfigurationWithUseSbtShell(profile) && executorId != DefaultDebugExecutor.EXECUTOR_ID

  override def execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback, state: RunProfileState): Unit = {
    state match {
      case sbtState: SbtCommandLineState =>
        if (sbtState.configuration.useSbtShell) {
          delegateExecutionToSbtShell(environment, sbtState)
        } else {
          super.execute(environment, callback, state)
        }
      case _ =>
    }
  }

  /**
   * @see [[com.intellij.execution.impl.RunConfigurationBeforeRunProvider.doRunTask]]
   */
  private def delegateExecutionToSbtShell(environment: ExecutionEnvironment, sbtState: SbtCommandLineState): Unit = {
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

object SbtProgramRunner {
  private class DummyProcessHandler extends ProcessHandler {
    override def destroyProcessImpl(): Unit = ()

    override def detachProcessImpl(): Unit = ()

    override def detachIsDefault(): Boolean = false

    @Nullable override def getProcessInput: OutputStream = null
  }
}