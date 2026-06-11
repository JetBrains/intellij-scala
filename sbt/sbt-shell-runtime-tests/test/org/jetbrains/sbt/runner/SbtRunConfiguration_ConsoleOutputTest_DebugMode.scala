package org.jetbrains.sbt.runner

import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}

class SbtRunConfiguration_ConsoleOutputTest_DebugMode extends SbtRunConfiguration_ConsoleOutputTestBase {

  override protected def executionMode: ExecutionMode = ExecutionMode.Debug

  def testDebugMode_NonShell_ConsoleOutputContainsSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.NoShell,
    ))

  def testDebugMode_OldSbtShellAlreadyRunning_ConsoleOutputContainsOnlyOwnSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.OldShell,
    ))

  def testDebugMode_NewSbtShellAlreadyRunning_ConsoleOutputContainsOnlyOwnSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.NewShell,
    ))

  def testDebugMode_OldSbtShellStartedByRunConfiguration_ConsoleOutputContainsSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.OldShell,
    ).copy(prestartSbtShell = false))

  def testDebugMode_NewSbtShellStartedByRunConfiguration_ConsoleOutputContainsSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.NewShell,
    ).copy(prestartSbtShell = false))
}
