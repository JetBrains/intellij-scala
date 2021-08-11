package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.{RunProfile, RunProfileState, RunnerSettings}
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.{ExecutionEnvironment, GenericProgramRunner, ProgramRunner}

/**
  * User: Dmitry.Naydanov
  * Date: 14.08.18.
  */
class SbtProgramRunner extends GenericProgramRunner[RunnerSettings] with SbtProgramRunnerBase {
  override def getRunnerId: String = "SbtProgramRunner"

  override def execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback, state: RunProfileState): Unit = {
    state match {
      case sbtState: SbtCommandLineState => 
        if (sbtState.configuration.useSbtShell) submitCommands(environment, sbtState) else super.execute(environment, callback, state)
      case _ =>
    }
  }

  override def canRun(executorId: String, profile: RunProfile): Boolean =
    checkRunProfile(profile) && executorId != DefaultDebugExecutor.EXECUTOR_ID
}
