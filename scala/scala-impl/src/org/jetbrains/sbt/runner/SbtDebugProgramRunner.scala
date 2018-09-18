package org.jetbrains.sbt.runner

import com.intellij.debugger.engine.RemoteStateState
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.{ExecutionResult, Executor}
import com.intellij.execution.configurations.{RemoteConnection, RunProfile, RunProfileState, RunnerSettings}
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication}

/**
  * User: Dmitry.Naydanov
  * Date: 14.08.18.
  */
class SbtDebugProgramRunner extends GenericDebuggerRunner {
  override def createContentDescriptor(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor = {
    state match {
      case sbtState: SbtSimpleCommandLineState =>
        SbtProcessManager.forProject(environment.getProject).acquireShellRunner.getDebugConnection.foreach {
          connection =>
            import scala.concurrent.ExecutionContext.Implicits.global
            
            val state = new MyTrojanRemoteState(environment.getProject, connection)
            val attach = attachVirtualMachine(state, environment, connection, true)
            SbtShellCommunication.forProject(environment.getProject).command(sbtState.commands).onComplete {
              _ => 
                state.detach()
            }
            return attach
        }
      case _ => 
    }
    
    super.createContentDescriptor(state, environment)
  }

  override def canRun(executorId: String, profile: RunProfile): Boolean =
    profile match {
      case sbtConf: SbtRunConfiguration if sbtConf.getUseSbtShell => executorId == DefaultDebugExecutor.EXECUTOR_ID
      case _ => false
    }

  override def getRunnerId: String = "SbtDebugProgramRunner"
  
  private class MyTrojanRemoteState(project: Project, connection: RemoteConnection) extends RemoteStateState(project, connection) {
    private var execResult: Option[ExecutionResult] = None

    override def execute(executor: Executor, runner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
      val er = super.execute(executor, runner)
      execResult = Option(er)
      er
    }
    
    def detach() {
      execResult.foreach {
        result => 
          Option(result.getProcessHandler).foreach(_.detachProcess())
      }
    }
  }
}
