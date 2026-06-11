package org.jetbrains.sbt.runner

import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}

class SbtRunConfiguration_ConsoleOutputTest_RunMode extends SbtRunConfiguration_ConsoleOutputTestBase {

  override protected def executionMode: ExecutionMode = ExecutionMode.Run

  def testRunMode_NonShell_ConsoleOutputContainsSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.NoShell,
    ))

  def testRunMode_OldSbtShellAlreadyRunning_ConsoleOutputContainsOnlyOwnSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.OldShell,
    ))

  def testRunMode_NewSbtShellAlreadyRunning_ConsoleOutputContainsOnlyOwnSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.NewShell,
    ))

  def testRunMode_OldSbtShellStartedByRunConfiguration_ConsoleOutputContainsSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.OldShell,
    ).copy(prestartSbtShell = false))

  def testRunMode_NewSbtShellStartedByRunConfiguration_ConsoleOutputContainsSbtOutput(): Unit =
    assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(TestExecutionOptions(
      executionMode,
      SbtProcessMode.NewShell,
    ).copy(prestartSbtShell = false))
}
