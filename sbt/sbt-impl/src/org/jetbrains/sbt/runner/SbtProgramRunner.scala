package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.{RunProfile, RunProfileState, RunnerSettings}
import com.intellij.execution.runners.{ExecutionEnvironment, GenericProgramRunner}
import com.intellij.execution.ui.RunContentDescriptor

/**
 * Handles SBT task run configurations only when they are delegated to the SBT shell.
 *
 * When [[SbtRunConfiguration.useSbtShell]] is disabled, this runner intentionally does not claim the configuration in [[canRun]].<br>
 * In that mode the standard platform run runner executes the [[SbtCommandLineState]] as a
 * [[com.intellij.execution.configurations.JavaCommandLineState]],
 * using the Java command line assembled by [[SbtCommandLineState.createJavaParameters]].
 *
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
