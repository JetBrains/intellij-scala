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

  def testRunMode_OldSbtShellStartedByRunConfiguration_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell).copy(prestartSbtShell = false),
      expectedHintPresent = true,
    )

  def testRunMode_NewSbtShellStartedByRunConfiguration_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell).copy(prestartSbtShell = false),
      expectedHintPresent = true,
    )

  def testRunMode_OldSbtShellAlreadyRunningAndIdle_ConsoleOutputDoesNotContainWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell),
      expectedHintPresent = false,
    )

  def testRunMode_NewSbtShellAlreadyRunningAndIdle_ConsoleOutputDoesNotContainWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell),
      expectedHintPresent = false,
    )

  def testRunMode_OldSbtShellAlreadyRunningAndBusy_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell),
      expectedHintPresent = true,
      keepPrestartedShellBusy = true,
    )

  def testRunMode_NewSbtShellAlreadyRunningAndBusy_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell),
      expectedHintPresent = true,
      keepPrestartedShellBusy = true,
    )

  def testRunMode_OldSbtShellAlreadyRunningManualCommand_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell),
      expectedHintPresent = true,
      keepPrestartedShellBusyWithManualCommand = true,
    )

  def testRunMode_NewSbtShellAlreadyRunningManualCommand_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell),
      expectedHintPresent = true,
      keepPrestartedShellBusyWithManualCommand = true,
    )

  def testRunMode_OldSbtShellAlreadyRunningRunConfiguration_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell),
      expectedHintPresent = true,
      keepPrestartedShellBusyWithRunConfiguration = true,
    )

  def testRunMode_NewSbtShellAlreadyRunningRunConfiguration_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell),
      expectedHintPresent = true,
      keepPrestartedShellBusyWithRunConfiguration = true,
    )
}
