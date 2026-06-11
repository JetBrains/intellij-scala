package org.jetbrains.sbt.runner

import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.process.mock.MockSbtProcessCommands
import org.jetbrains.sbt.runner.SbtRunConfiguration_ConsoleOutputTestBase.*
import org.jetbrains.sbt.runner.TestExecutionOptions.ExecutionMode
import org.jetbrains.sbt.runner.utils.{ExecutionDiagnostics, RunConfigInTestsExecutor, RunConfigurationExecutionObserver, SbtRunConfigurationTestFactory}
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.junit.Assert.{assertFalse, assertTrue}

import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

abstract class SbtRunConfiguration_ConsoleOutputTestBase extends SbtRunConfiguration_MockedProcess_ExecutionTestBase {

  protected def executionMode: ExecutionMode

  protected final def assertRunConfigurationConsoleContainsOnlyOwnSbtOutput(options: TestExecutionOptions): Unit = {
    val runCommandFile = commandFile("run-configuration")
    val runCommand = MockSbtProcessCommands.waitForFileCommand(runCommandFile)
    val expectedRunCommandOutput = MockSbtProcessCommands.waitingForFileOutput(runCommandFile)

    try {
      // The mock command waits for this file, so pre-creating it makes the run configuration finish deterministically
      // after the process has printed the marker output we assert below.
      Files.writeString(runCommandFile, "release")
      initSbtShellIfNeeded(options)
      waitUntilSbtShellIsReadyIfNeeded(options)

      // Prestarted shell cases first produce output outside this run configuration; the assertions below verify
      // that such already-existing shell output is not replayed into the run configuration console.
      val outputProducedBeforeRunConfiguration = produceAlreadyRunningShellOutputIfNeeded(options)
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
      withExecutionDiagnostics(Some(executionObserver)) {
        executionObserver.awaitSuccessfulTermination(timeout = 10.seconds)
        waitUntilConsoleContains(executionObserver, expectedRunCommandOutput)
        waitUntilDebuggerOutputIsCapturedIfNeeded(options, executionObserver)
      }

      val runConfigurationConsoleOutput = executionObserver.consoleOutputSnapshot
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
        assertSbtShellProcessOutputContainsCommandMarkerIfNeeded(options, expectedRunCommandOutput)
      }
    } finally {
      tearDownForTestCase(options)
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

  private def waitUntilConsoleContains(
    executionObserver: RunConfigurationExecutionObserver,
    expectedOutput: String,
  ): Unit =
    AwaitTestUtils.waitForConditionOrFail(
      5.seconds,
      s"Timed out waiting for run configuration console output to contain: $expectedOutput",
    ) { () =>
      executionObserver.consoleOutputSnapshot.contains(expectedOutput)
    }

  private def waitUntilDebuggerOutputIsCapturedIfNeeded(
    options: TestExecutionOptions,
    executionObserver: RunConfigurationExecutionObserver,
  ): Unit =
    if (options.expectsRunConfigurationDebugConnection) {
      AwaitTestUtils.waitForConditionOrFail(
        5.seconds,
        "Timed out waiting for debug attach/detach output in the run configuration console",
      ) { () =>
        val output = executionObserver.consoleOutputSnapshot
        output.contains(DebuggerConnectedOutput) && output.contains(DebuggerDisconnectedOutput)
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

  private def configurationName(options: TestExecutionOptions): String =
    s"sbt console output (${options.executionMode.displayName}, ${sbtShellModeDisplayName(options)}, prestart=${options.prestartSbtShell})"

  private def assertContains(clue: String, output: String, expectedFragment: String): Unit =
    assertTrue(
      s"""$clue
         |Expected output fragment:
         |${expectedFragment.indent(2)}Actual output:
         |${output.indent(2)}""".stripMargin,
      output.contains(expectedFragment),
    )

  private def assertDoesNotContain(clue: String, output: String, unexpectedFragment: String): Unit =
    assertFalse(
      s"""$clue
         |Unexpected output fragment:
         |${unexpectedFragment.indent(2)}Actual output:
         |${output.indent(2)}""".stripMargin,
      output.contains(unexpectedFragment),
    )
}

private object SbtRunConfiguration_ConsoleOutputTestBase {
  private val DebuggerConnectedOutput = "Connected to the target VM"
  private val DebuggerDisconnectedOutput = "Disconnected from the target VM"
}
