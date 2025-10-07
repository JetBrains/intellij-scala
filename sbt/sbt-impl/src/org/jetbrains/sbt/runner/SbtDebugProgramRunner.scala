package org.jetbrains.sbt.runner

import com.intellij.debugger.engine.RemoteStateState
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.configurations.{RemoteConnection, RunProfile, RunProfileState}
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.{ExecutionResult, Executor}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.shell.SbtProcessManager

/**
 * @see [[org.jetbrains.sbt.runner.SbtProgramRunner]]
 */
class SbtDebugProgramRunner extends GenericDebuggerRunner with SbtProgramRunnerBase {
  override def createContentDescriptor(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor =
    state match {
      case sbtState: SbtCommandLineState =>
        if (sbtState.configuration.useSbtShell)
          createContentDescriptorForSbtShellDelegation(environment, sbtState)
        else
          super.createContentDescriptor(state, environment)
      case _ =>
        null
    }

  private def createContentDescriptorForSbtShellDelegation(environment: ExecutionEnvironment, sbtState: SbtCommandLineState): RunContentDescriptor = {
    val shellRunner = SbtProcessManager.forProject(environment.getProject).acquireShellRunner()
    val shellDebugConnection = shellRunner.getDebugConnection
    // ATTENTION: currently, if sbt shell was not launched with the debug agent,
    // we won't notify user anyhow that we haven't launched sbt in the debug mode
    shellDebugConnection.map { connection =>
      createContentDescriptorForDebugConnection(environment, sbtState, connection)
    }.orNull
  }

  private def createContentDescriptorForDebugConnection(
    environment: ExecutionEnvironment,
    sbtState: SbtCommandLineState,
    connection: RemoteConnection
  ): RunContentDescriptor = {
    val state = new MyTrojanRemoteState(environment.getProject, connection)
    val attach = attachVirtualMachine(state, environment, connection, true)

    import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext

    ApplicationManager.getApplication.executeOnPooledThread((() => {
      val commandFuture = submitCommands(environment, sbtState)
      commandFuture.onComplete { _ =>
        state.detach()
      }
    }): Runnable)

    attach
  }

  override def canRun(executorId: String, profile: RunProfile): Boolean =
    isSbtRunConfigurationWithUseSbtShell(profile) && executorId == DefaultDebugExecutor.EXECUTOR_ID

  override def getRunnerId: String = "SbtDebugProgramRunner"
  
  private class MyTrojanRemoteState(project: Project, connection: RemoteConnection) extends RemoteStateState(project, connection) {
    private var execResult: Option[ExecutionResult] = None

    override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
      val er = super.execute(executor, runner)
      execResult = Option(er)
      er
    }
    
    def detach(): Unit = {
      execResult.foreach {
        result => 
          Option(result.getProcessHandler).foreach(_.detachProcess())
      }
    }
  }
}
