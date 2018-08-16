package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.{RunProfile, RunProfileState, RunnerSettings}
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.{BaseProgramRunner, ExecutionEnvironment, ProgramRunner}
import org.jetbrains.sbt.shell.SbtShellCommunication

/**
  * User: Dmitry.Naydanov
  * Date: 14.08.18.
  */
class SbtProgramRunner extends BaseProgramRunner[RunnerSettings] {
  override def getRunnerId: String = "SbtProgramRunner"

  override def execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback, state: RunProfileState): Unit = {
    state match {
      case sbtState: SbtSimpleCommandLineState =>
        SbtShellCommunication.forProject(environment.getProject).command(sbtState.commands)
      case _ =>
    }
  }

  override def canRun(executorId: String, profile: RunProfile): Boolean =
    profile match {
      case sbtConf: SbtRunConfiguration if sbtConf.getUseSbtShell => executorId != DefaultDebugExecutor.EXECUTOR_ID
      case _ => false
    }
}
