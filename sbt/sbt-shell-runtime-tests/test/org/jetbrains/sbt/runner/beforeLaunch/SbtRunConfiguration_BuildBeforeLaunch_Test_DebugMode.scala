package org.jetbrains.sbt.runner.beforeLaunch

import org.jetbrains.sbt.runner.TestExecutionOptions
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}

class SbtRunConfiguration_BuildBeforeLaunch_Test_DebugMode extends SbtRunConfiguration_BuildBeforeLaunch_TestBase {
  def testDebugMode_NoSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfiguration_DoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NoShell,
    ))

  def testDebugMode_OldSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfiguration_DoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.OldShell,
    ))

  def testDebugMode_NewSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfiguration_DoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NewShell,
    ))

  def testDebugMode_OldSbtShellStartedByRunConfiguration_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfiguration_DoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.OldShell,
    ).copy(prestartSbtShell = false))

  def testDebugMode_NewSbtShellStartedByRunConfiguration_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfiguration_DoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NewShell,
    ).copy(prestartSbtShell = false))

  def testDebugMode_NoSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NoShell,
    ))

  def testDebugMode_OldSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.OldShell,
    ))

  def testDebugMode_NewSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NewShell,
    ))

  def testDebugMode_OldSbtShellStartedByRunConfiguration_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.OldShell,
    ).copy(prestartSbtShell = false))

  def testDebugMode_NewSbtShellStartedByRunConfiguration_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NewShell,
    ).copy(prestartSbtShell = false))
}
