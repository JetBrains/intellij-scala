package org.jetbrains.sbt.runner

import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.process.mock.MockSbtProcessCommands
import org.jetbrains.sbt.runner.consoleOutput.ConsoleOutputAssertions.*
import org.jetbrains.sbt.runner.consoleOutput.RunConfigurationConsoleOutputAwaiter.awaitFinalConsoleOutput
import org.jetbrains.sbt.runner.consoleOutput.SbtShellToolWindowActivationTestUtil.*
import org.jetbrains.sbt.runner.TestExecutionOptions.ExecutionMode
import org.jetbrains.sbt.runner.console.SbtShellWaitingForReadyHint
import org.jetbrains.sbt.runner.utils.{ExecutionDiagnostics, RunConfigInTestsExecutor, RunConfigurationExecutionObserver, SbtRunConfigurationTestFactory}
import org.jetbrains.sbt.shell.SbtShellCommunication

import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

abstract class SbtRunConfiguration_ConsoleOutputTestBase extends SbtRunConfiguration_MockedProcess_ExecutionTestBase {

  protected def executionMode: ExecutionMode

  protected final def assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(options: TestExecutionOptions): Unit = {
    val runCommandFile = commandFile("run-configuration")
    val runCommand = MockSbtProcessCommands.waitForFileCommand(runCommandFile)
    val expectedRunCommandOutput = MockSbtProcessCommands.waitingForFileOutput(runCommandFile)
    val activationProbe = installSbtShellToolWindowActivationProbeIfNeeded(
      options,
      getProject,
      getTestRootDisposable,
    )

    try {
      // The mock command waits for this file, so pre-creating it makes the run configuration finish deterministically
      // after the process has printed the marker output we assert below.
      Files.writeString(runCommandFile, "release")
      initSbtShellIfNeeded(options)
      waitUntilSbtShellIsReadyIfNeeded(options)

      // Prestarted shell cases first produce output outside this run configuration; the assertions below verify
      // that such already-existing shell output is not replayed into the run configuration console.
      val outputProducedBeforeRunConfiguration = produceAlreadyRunningShellOutputIfNeeded(options)
      // Treat everything above as an explicit test setup. The run configuration itself must not add any new
      // show/activate/focus requests for the sbt shell tool window.
      val toolWindowActivationBaseline = captureSbtShellToolWindowActivationBaselineIfNeeded(activationProbe)
      // Raw SBT diagnostics are reset after the warm-up so process-output assertions describe only this execution.
      clearSbtProcessOutputDiagnostics()

      // Create the run configuration after shell warm-up and subscribe the observer before execution so the captured
      // console text belongs to this run configuration, not to the preparatory shell command.
      val runConfigAndSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
        getProject,
        configurationName = configurationName(options),
        sbtCommands = runCommand,
        useSbtShellInRunConfig = options.useSbtShellInRunConfig,
      )
      val executionObserver = RunConfigurationExecutionObserver.subscribe(runConfigAndSettings, getTestRootDisposable)

      // The descriptor callback records the actual run content console, which may include IDE-added console text
      // that is intentionally different from raw process output.
      RunConfigInTestsExecutor.executeTopLevelConfiguration(
        getProject,
        runConfigAndSettings,
        options.executionMode.executor,
        descriptorCallback = executionObserver.recordRunContentDescriptor,
      )

      // Process termination, console rendering, and debug attach/detach text are delivered asynchronously, so wait
      // for every signal that participates in the final console-output assertions.
      val runConfigurationConsoleOutput = awaitFinalConsoleOutput(
        options,
        executionObserver,
        expectedRunCommandOutput,
      )

      withExecutionDiagnostics(Some(executionObserver)) {
        // This is the product contract under test: the run configuration console shows its own command/debug output,
        // while raw SBT diagnostics remain available for assertions about the underlying shell process.
        assertContains(
          clue = "Run configuration console output must contain output produced by its own sbt command",
          output = runConfigurationConsoleOutput,
          expectedFragment = expectedRunCommandOutput,
        )
        outputProducedBeforeRunConfiguration.foreach { unexpectedOutput =>
          assertDoesNotContain(
            clue = "Run configuration console output must not contain output produced by the already-running sbt shell before execution",
            output = runConfigurationConsoleOutput,
            unexpectedFragment = unexpectedOutput,
          )
        }
        assertExpectedDebugOutput(options, executionObserver)
        assertSbtShellToolWindowWasNotOpenedByRunConfigurationIfNeeded(
          activationProbe,
          toolWindowActivationBaseline,
          sbtShellModeDisplayName(options),
        )
        assertSbtShellProcessOutputContainsCommandMarkerIfNeeded(options, expectedRunCommandOutput)
      }
    } finally {
      tearDownForTestCase(options)
    }
  }

  protected final def assertSbtShellWaitingHintVisibility(
    options: TestExecutionOptions,
    expectedHintPresent: Boolean,
    keepPrestartedShellBusy: Boolean = false,
    keepPrestartedShellBusyWithManualCommand: Boolean = false,
    keepPrestartedShellBusyWithRunConfiguration: Boolean = false,
  ): Unit = {
    val runCommandFile = commandFile("waiting-hint-run-configuration")
    val runCommand = MockSbtProcessCommands.waitForFileCommand(runCommandFile)
    val expectedRunCommandOutput = MockSbtProcessCommands.waitingForFileOutput(runCommandFile)
    val busyShellReleaseFile = commandFile("waiting-hint-busy-shell")
    val busyShellResumedOutput = MockSbtProcessCommands.resumedAfterWaitingForFileOutput(busyShellReleaseFile)
    var busyShellOutputFuture = Option.empty[scala.concurrent.Future[String]]
    var busyRunConfigurationObserver = Option.empty[RunConfigurationExecutionObserver]
    val activationProbe = installSbtShellToolWindowActivationProbeIfNeeded(
      options,
      getProject,
      getTestRootDisposable,
    )

    try {
      // Make the run-configuration command complete deterministically after the console/hint assertions have a chance to observe it.
      Files.writeString(runCommandFile, "release")

      // Prepare the requested shell mode. Prestarted cases must reach Idle before execution so absence/presence of the hint
      // depends only on whether the shell is busy at command submission time.
      initSbtShellIfNeeded(options)
      waitUntilSbtShellIsReadyIfNeeded(options)

      // Simulate an already-running shell that cannot accept the run-configuration command immediately.
      // This is the prestarted-shell counterpart of a fresh shell still initializing.
      if (keepPrestartedShellBusy || keepPrestartedShellBusyWithManualCommand || keepPrestartedShellBusyWithRunConfiguration) {
        val busyShellCommand = MockSbtProcessCommands.waitForFileCommand(busyShellReleaseFile)
        val sbtShellCommunication = SbtShellCommunication.forProject(getProject)
        if (keepPrestartedShellBusyWithManualCommand) {
          sbtShellCommunication.send(s"$busyShellCommand\n")
        } else if (keepPrestartedShellBusyWithRunConfiguration) {
          busyRunConfigurationObserver = Some(startBusyRunConfiguration(options, busyShellCommand))
        } else {
          busyShellOutputFuture = Some(sbtShellCommunication.runAndCollectOutput(busyShellCommand))
        }
        waitForSbtProcessOutput(
          expectedOutput = MockSbtProcessCommands.waitingForFileOutput(busyShellReleaseFile),
          timeoutMessage = "Timed out waiting for the prestarted mock SBT shell to become busy",
        )
      }

      // Drop setup noise from raw SBT diagnostics so the final assertions describe only this run configuration.
      val toolWindowActivationBaseline = captureSbtShellToolWindowActivationBaselineIfNeeded(activationProbe)
      clearSbtProcessOutputDiagnostics()

      // Subscribe before execution and record the descriptor callback because the hint is printed directly to the run/debug console,
      // not through the run-configuration process handler or the raw SBT process.
      val runConfigAndSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
        getProject,
        configurationName = configurationName(options),
        sbtCommands = runCommand,
        useSbtShellInRunConfig = options.useSbtShellInRunConfig,
      )
      val executionObserver = RunConfigurationExecutionObserver.subscribe(runConfigAndSettings, getTestRootDisposable)

      RunConfigInTestsExecutor.executeTopLevelConfiguration(
        getProject,
        runConfigAndSettings,
        options.executionMode.executor,
        descriptorCallback = executionObserver.recordRunContentDescriptor,
      )

      // In busy-shell scenarios, wait for the user-visible signal under test before releasing the artificial blocker.
      // Debug-mode command submission can wait for debugger attach first, while the waiting hint is printed earlier.
      waitForSbtShellWaitingHintIfNeeded(options, expectedHintPresent, executionObserver)

      // Let the artificial busy-shell command finish so the run-configuration command can leave the queue and terminate.
      releaseWaitingMockCommand(busyShellReleaseFile)
      busyShellOutputFuture.foreach { future =>
        AwaitTestUtils.waitFutureOrFail(
          future,
          10.seconds,
          "waiting for the busy mock SBT shell command to finish",
        )
      }
      busyRunConfigurationObserver.foreach { observer =>
        observer.awaitSuccessfulTermination(timeout = 10.seconds)
        assertExpectedDebugOutput(options, observer)
      }

      // Wait for all user-visible output channels that participate in assertions: command output, and in Debug mode,
      // debugger attach/detach messages that arrive independently from SBT shell command output.
      val runConfigurationConsoleOutput = awaitFinalConsoleOutput(
        options,
        executionObserver,
        expectedRunCommandOutput,
      )

      // Verify the product contract: the hint belongs only to the IDE console, while regular command output remains
      // visible both in the run configuration console and in raw SBT diagnostics.
      val runConfigurationProcessOutput = executionObserver.processOutputSnapshot
      val sbtProcessOutput = ExecutionDiagnostics.sbtProcessOutputSnapshot
      withExecutionDiagnostics(Some(executionObserver)) {
        assertContains(
          clue = "Run configuration console output must contain regular command output",
          output = runConfigurationConsoleOutput,
          expectedFragment = expectedRunCommandOutput,
        )
        if (keepPrestartedShellBusyWithManualCommand || keepPrestartedShellBusyWithRunConfiguration) {
          assertDoesNotContain(
            clue = "Run configuration console output must not contain output produced by another SBT shell command",
            output = runConfigurationConsoleOutput,
            unexpectedFragment = busyShellResumedOutput,
          )
        }
        assertSbtShellWaitingHintPresence(
          runConfigurationConsoleOutput,
          expectedHintPresent,
          SbtShellWaitingForReadyHint.HintText,
        )
        assertSbtShellWaitingHintInlayPresence(
          expectedHintPresent,
          SbtShellWaitingForReadyHint.HintText,
          executionObserver.consoleInlayOffsetsAfterTextSnapshot(SbtShellWaitingForReadyHint.HintText),
        )
        assertSbtShellToolWindowWasNotOpenedByRunConfigurationIfNeeded(
          activationProbe,
          toolWindowActivationBaseline,
          sbtShellModeDisplayName(options),
        )
        assertExpectedDebugOutput(options, executionObserver)
        assertDoesNotContain(
          clue = "Run configuration process output must not contain the IDE-generated sbt shell waiting hint",
          output = runConfigurationProcessOutput,
          unexpectedFragment = SbtShellWaitingForReadyHint.HintText,
        )
        assertContains(
          clue = "Raw SBT process diagnostics must contain regular command output",
          output = sbtProcessOutput,
          expectedFragment = expectedRunCommandOutput,
        )
        assertDoesNotContain(
          clue = "Raw SBT process diagnostics must not contain the IDE-generated sbt shell waiting hint",
          output = sbtProcessOutput,
          unexpectedFragment = SbtShellWaitingForReadyHint.HintText,
        )
      }
    } finally {
      // Always unblock the mock shell command before teardown; otherwise a failed assertion could leave the shared shell queue stuck.
      releaseWaitingMockCommand(busyShellReleaseFile)
      tearDownForTestCase(options)
    }
  }

  private def waitForSbtShellWaitingHintIfNeeded(
    options: TestExecutionOptions,
    expectedHintPresent: Boolean,
    executionObserver: RunConfigurationExecutionObserver,
  ): Unit = {
    if (!expectedHintPresent) {
      return
    }

    withExecutionDiagnostics(Some(executionObserver)) {
      AwaitTestUtils.waitForConditionOrFail(
        30.seconds,
        s"Timed out waiting for the ${sbtShellModeDisplayName(options)} run configuration console to show the sbt shell waiting hint",
      ) { () =>
        executionObserver.consoleOutputSnapshot.contains(SbtShellWaitingForReadyHint.HintText)
      }
    }
  }

  private def produceAlreadyRunningShellOutputIfNeeded(options: TestExecutionOptions): Option[String] = {
    // Warm-up output is meaningful only when the run configuration delegates to an already-started SBT shell.
    if (!options.useSbtShellInRunConfig || !options.prestartSbtShell) {
      return None
    }

    val warmupCommandFile = commandFile("already-running-shell")
    Files.writeString(warmupCommandFile, "release")

    val expectedWarmupOutput = MockSbtProcessCommands.waitingForFileOutput(warmupCommandFile)
    // Simulate a shell that already printed command output before the run configuration was started.
    val warmupOutputFuture = SbtShellCommunication
      .forProject(getProject)
      .runAndCollectOutput(MockSbtProcessCommands.waitForFileCommand(warmupCommandFile))

    val warmupOutput = withExecutionDiagnostics() {
      AwaitTestUtils.waitFutureOrFail(
        warmupOutputFuture,
        10.seconds,
        "waiting for the already-running sbt shell warm-up command to finish",
      )
    }
    withExecutionDiagnostics() {
      assertContains(
        clue = "The already-running sbt shell warm-up command must produce output before the run configuration starts",
        output = warmupOutput,
        expectedFragment = expectedWarmupOutput,
      )
    }
    // The caller uses this marker as forbidden console output for the actual run configuration.
    Some(expectedWarmupOutput)
  }

  private def startBusyRunConfiguration(
    options: TestExecutionOptions,
    busyShellCommand: String,
  ): RunConfigurationExecutionObserver = {
    val busyRunConfigAndSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
      getProject,
      configurationName = s"${configurationName(options)} busy shell owner",
      sbtCommands = busyShellCommand,
      useSbtShellInRunConfig = options.useSbtShellInRunConfig,
    )
    val busyExecutionObserver = RunConfigurationExecutionObserver.subscribe(busyRunConfigAndSettings, getTestRootDisposable)

    RunConfigInTestsExecutor.executeTopLevelConfiguration(
      getProject,
      busyRunConfigAndSettings,
      options.executionMode.executor,
      descriptorCallback = busyExecutionObserver.recordRunContentDescriptor,
    )

    busyExecutionObserver
  }

  private def waitForSbtProcessOutput(expectedOutput: String, timeoutMessage: String): Unit =
    withExecutionDiagnostics() {
      AwaitTestUtils.waitForConditionOrFail(5.seconds, timeoutMessage) { () =>
        ExecutionDiagnostics.sbtProcessOutputSnapshot.contains(expectedOutput)
      }
    }

  private def assertSbtShellProcessOutputContainsCommandMarkerIfNeeded(
    options: TestExecutionOptions,
    expectedRunCommandOutput: String,
  ): Unit =
    if (options.useSbtShellInRunConfig) {
      val sbtProcessOutput = ExecutionDiagnostics.sbtProcessOutputSnapshot
      assertContains(
        clue = "Sbt shell process output must contain output produced by the run configuration command",
        output = sbtProcessOutput,
        expectedFragment = expectedRunCommandOutput,
      )
    }

  private def commandFile(label: String): Path =
    getTestProjectPath.resolve(s"${getTestName(false)}-$label.release")

  private def releaseWaitingMockCommand(releaseFile: Path): Unit =
    if (!Files.exists(releaseFile)) {
      Files.writeString(releaseFile, "release")
    }

  private def configurationName(options: TestExecutionOptions): String =
    s"sbt console output (${options.executionMode.displayName}, ${sbtShellModeDisplayName(options)}, prestart=${options.prestartSbtShell})"
}
