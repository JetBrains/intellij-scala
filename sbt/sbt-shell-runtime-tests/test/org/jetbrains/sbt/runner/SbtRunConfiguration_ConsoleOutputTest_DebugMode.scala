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

  def testDebugMode_OldSbtShellStartedByRunConfiguration_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell).copy(prestartSbtShell = false),
      expectedHintPresent = true,
    )

  def testDebugMode_NewSbtShellStartedByRunConfiguration_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell).copy(prestartSbtShell = false),
      expectedHintPresent = true,
    )

  def testDebugMode_OldSbtShellAlreadyRunningAndIdle_ConsoleOutputDoesNotContainWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell),
      expectedHintPresent = false,
    )

  def testDebugMode_NewSbtShellAlreadyRunningAndIdle_ConsoleOutputDoesNotContainWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell),
      expectedHintPresent = false,
    )

  def testDebugMode_OldSbtShellAlreadyRunningAndBusy_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell),
      expectedHintPresent = true,
      keepPrestartedShellBusy = true,
    )

  def testDebugMode_NewSbtShellAlreadyRunningAndBusy_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell),
      expectedHintPresent = true,
      keepPrestartedShellBusy = true,
    )

  def testDebugMode_OldSbtShellAlreadyRunningManualCommand_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell),
      expectedHintPresent = true,
      keepPrestartedShellBusyWithManualCommand = true,
    )

  def testDebugMode_NewSbtShellAlreadyRunningManualCommand_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell),
      expectedHintPresent = true,
      keepPrestartedShellBusyWithManualCommand = true,
    )

  def testDebugMode_OldSbtShellAlreadyRunningRunConfiguration_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.OldShell),
      expectedHintPresent = true,
      keepPrestartedShellBusyWithRunConfiguration = true,
    )

  def testDebugMode_NewSbtShellAlreadyRunningRunConfiguration_ConsoleOutputContainsWaitingHint(): Unit =
    assertSbtShellWaitingHintVisibility(
      TestExecutionOptions(executionMode, SbtProcessMode.NewShell),
      expectedHintPresent = true,
      keepPrestartedShellBusyWithRunConfiguration = true,
    )
}
