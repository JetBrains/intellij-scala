package org.jetbrains.sbt.runner.beforeLaunch

import org.jetbrains.sbt.runner.TestExecutionOptions
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}

class SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_RunMode extends SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_TestBase {

  def testRunMode_NoSbtShell_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      runModeOptions(SbtProcessMode.NoShell)
    )

  def testRunMode_OldSbtShell_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      runModeOptions(SbtProcessMode.OldShell)
    )

  def testRunMode_NewSbtShell_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      runModeOptions(SbtProcessMode.NewShell)
    )

  def testRunMode_NoSbtShell_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      runModeOptions(SbtProcessMode.NoShell)
    )

  def testRunMode_OldSbtShell_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      runModeOptions(SbtProcessMode.OldShell)
    )

  def testRunMode_NewSbtShell_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      runModeOptions(SbtProcessMode.NewShell)
    )

  def testRunMode_NoSbtShell_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      runModeOptions(SbtProcessMode.NoShell)
    )

  def testRunMode_OldSbtShell_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      runModeOptions(SbtProcessMode.OldShell)
    )

  def testRunMode_NewSbtShell_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      runModeOptions(SbtProcessMode.NewShell)
    )

  private def runModeOptions(sbtProcessMode: SbtProcessMode): TestExecutionOptions =
    TestExecutionOptions(
      ExecutionMode.Run,
      sbtProcessMode,
      enableDebuggingInShell = false,
    )
}
