package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.{RunProfile, RunProfileState, RunnerSettings}
import com.intellij.execution.runners.{ExecutionEnvironment, GenericProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor

/**
 * @see [[org.jetbrains.sbt.runner.SbtDebugProgramRunner]]
 */
class SbtProgramRunner extends GenericProgramRunner[RunnerSettings] with SbtProgramRunnerBase {

  override def getRunnerId: String = "SbtProgramRunner"

  override def canRun(executorId: String, profile: RunProfile): Boolean =
    isSbtRunConfigurationWithUseSbtShell(profile) && !isDebugExecutorId(executorId)

  override def doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor = {
    state match {
      case sbtState: SbtCommandLineState if sbtState.configuration.useSbtShell =>
        delegateExecutionToSbtShell(environment, sbtState)
      case _ =>
        super.doExecute(state, environment)
    }
  }
}
