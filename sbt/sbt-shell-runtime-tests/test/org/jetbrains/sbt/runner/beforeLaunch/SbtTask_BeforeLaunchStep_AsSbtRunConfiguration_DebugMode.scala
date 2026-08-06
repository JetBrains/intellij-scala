package org.jetbrains.sbt.runner.beforeLaunch

import org.jetbrains.sbt.runner.TestExecutionOptions
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}

class SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_DebugMode extends SbtTask_BeforeLaunchStep_AsSbtRunConfiguration_TestBase {

  def testDebugMode_NoSbtShell_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      debugModeOptions(SbtProcessMode.NoShell)
    )

  def testDebugMode_OldSbtShell_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      debugModeOptions(SbtProcessMode.OldShell)
    )

  def testDebugMode_NewSbtShell_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      debugModeOptions(SbtProcessMode.NewShell)
    )

  def testDebugMode_NoSbtShell_WithDisabledSbtShellDebugging_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.NoShell)
    )

  def testDebugMode_OldSbtShell_WithDisabledSbtShellDebugging_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.OldShell)
    )

  def testDebugMode_NewSbtShell_WithDisabledSbtShellDebugging_UsesExpectedRunnerDelegation(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationUsesExpectedRunnerDelegation(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.NewShell)
    )

  def testDebugMode_NoSbtShell_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      debugModeOptions(SbtProcessMode.NoShell)
    )

  def testDebugMode_OldSbtShell_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      debugModeOptions(SbtProcessMode.OldShell)
    )

  def testDebugMode_NewSbtShell_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      debugModeOptions(SbtProcessMode.NewShell)
    )

  def testDebugMode_NoSbtShell_WithDisabledSbtShellDebugging_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.NoShell)
    )

  def testDebugMode_OldSbtShell_WithDisabledSbtShellDebugging_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.OldShell)
    )

  def testDebugMode_NewSbtShell_WithDisabledSbtShellDebugging_NotifiesExecutionListeners(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationNotifiesExecutionListeners(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.NewShell)
    )

  def testDebugMode_NoSbtShell_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      debugModeOptions(SbtProcessMode.NoShell)
    )

  def testDebugMode_OldSbtShell_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      debugModeOptions(SbtProcessMode.OldShell)
    )

  def testDebugMode_NewSbtShell_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      debugModeOptions(SbtProcessMode.NewShell)
    )

  def testDebugMode_NoSbtShell_WithDisabledSbtShellDebugging_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.NoShell)
    )

  def testDebugMode_OldSbtShell_WithDisabledSbtShellDebugging_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.OldShell)
    )

  def testDebugMode_NewSbtShell_WithDisabledSbtShellDebugging_DoesNotBlockDependentRunConfiguration(): Unit =
    assertSbtTaskAsBeforeLaunchStepOfAnotherConfigurationDoesNotBlockDependentRunConfiguration(
      debugModeOptionsWithDisabledSbtShellDebugging(SbtProcessMode.NewShell)
    )

  private def debugModeOptions(sbtProcessMode: SbtProcessMode): TestExecutionOptions =
    TestExecutionOptions(
      ExecutionMode.Debug,
      sbtProcessMode,
      enableDebuggingInShell = true,
    )

  private def debugModeOptionsWithDisabledSbtShellDebugging(sbtProcessMode: SbtProcessMode): TestExecutionOptions =
    TestExecutionOptions(
      ExecutionMode.Debug,
      sbtProcessMode,
      enableDebuggingInShell = false,
    )
}
