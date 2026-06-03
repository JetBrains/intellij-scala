package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.{RunProfile, RunProfileState, RunnerSettings}
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.{ExecutionEnvironment, GenericProgramRunner, ProgramRunner}

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
}