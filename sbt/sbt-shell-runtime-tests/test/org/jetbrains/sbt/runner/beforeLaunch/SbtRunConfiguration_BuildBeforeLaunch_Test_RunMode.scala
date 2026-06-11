package org.jetbrains.sbt.runner.beforeLaunch

import org.jetbrains.sbt.runner.TestExecutionOptions
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}

class SbtRunConfiguration_BuildBeforeLaunch_Test_RunMode extends SbtRunConfiguration_BuildBeforeLaunch_TestBase {
  def testRunMode_NoSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfiguration_DoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NoShell,
    ))

  def testRunMode_OldSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfiguration_DoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.OldShell,
    ))

  def testRunMode_NewSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfiguration_DoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NewShell,
    ))

  def testRunMode_NoSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NoShell,
    ))

  def testRunMode_OldSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.OldShell,
    ))

  def testRunMode_NewSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NewShell,
    ))
}