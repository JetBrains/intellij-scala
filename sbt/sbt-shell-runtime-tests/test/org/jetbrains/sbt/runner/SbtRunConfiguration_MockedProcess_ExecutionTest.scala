package org.jetbrains.sbt.runner

import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.{ExecutionManagerImpl, RunManagerImpl}
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.process.mock.MockSbtProcessCommands
import org.jetbrains.sbt.runner.SbtRunConfiguration_MockedProcess_ExecutionTest.*
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}
import org.jetbrains.sbt.runner.utils.{ExecutionDiagnostics, RunConfigInTestsExecutor, RunConfigurationExecutionObserver, SbtRunConfigurationTestFactory}
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.junit.Assert.{assertFalse, assertTrue}

import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

class SbtRunConfiguration_MockedProcess_ExecutionTest extends SbtRunConfiguration_MockedProcess_ExecutionTestBase {

  // See SCL-24434
  def testDebugMode_OldSbtShell_WithDisabledSbtShellDebugging_FailsToStart(): Unit =
    assertSbtRunConfiguration_WithDisabledSbtShellDebugging_FailsToStart(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.OldShell,
      enableDebuggingInShell = false,
    ))

  // See SCL-24434
  def testDebugMode_NewSbtShell_WithDisabledSbtShellDebugging_FailsToStart(): Unit =
    assertSbtRunConfiguration_WithDisabledSbtShellDebugging_FailsToStart(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NewShell,
      enableDebuggingInShell = false,
    ))

  private def assertSbtRunConfiguration_WithDisabledSbtShellDebugging_FailsToStart(options: TestExecutionOptions): Unit =
    try {
      val runConfigAndSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
        getProject,
        configurationName = s"sbt compile (${options.executionMode.displayName}, ${sbtShellModeDisplayName(options)}, debug disabled)",
        sbtCommands = "compile",
        useSbtShellInRunConfig = true,
      )

      initSbtShellIfNeeded(options)
      waitUntilSbtShellIsReadyIfNeeded(options)

      val executionObserver = RunConfigurationExecutionObserver.subscribe(runConfigAndSettings, getTestRootDisposable)

      RunConfigInTestsExecutor.executeTopLevelConfiguration(getProject, runConfigAndSettings, options.executionMode.executor)

      executionObserver.awaitFailedToStart(
        expectedCauseMessage = SbtBundle.message("debugging.for.sbt.shell.is.disabled.in.sbt.settings"),
        timeout = 10.seconds,
      )
    } finally {
      tearDownForTestCase(options)
    }

  def testRunMode_OldSbtShell_StoppingQueuedRunConfigurationCancelsShellCommand(): Unit =
    assertStoppingQueuedSbtShellRunConfigurationCancelsShellCommand(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.OldShell,
    ))

  def testRunMode_NewSbtShell_StoppingQueuedRunConfigurationCancelsShellCommand(): Unit =
    assertStoppingQueuedSbtShellRunConfigurationCancelsShellCommand(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NewShell,
    ))

  def testDebugMode_OldSbtShell_PreStartedShell_JdwpListeningBeforePrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.OldShell),
      MockSbtProcessCommands.JdwpListeningBeforePrompt,
    )

  def testDebugMode_NewSbtShell_PreStartedShell_JdwpListeningBeforePrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.NewShell),
      MockSbtProcessCommands.JdwpListeningBeforePrompt,
    )

  def testDebugMode_OldSbtShell_PreStartedShell_JdwpListeningAfterPrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.OldShell),
      MockSbtProcessCommands.JdwpListeningAfterPrompt,
    )

  def testDebugMode_NewSbtShell_PreStartedShell_JdwpListeningAfterPrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.NewShell),
      MockSbtProcessCommands.JdwpListeningAfterPrompt,
    )

  def testDebugMode_OldSbtShell_StartedByRunConfiguration_JdwpListeningBeforePrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.OldShell).copy(prestartSbtShell = false),
      MockSbtProcessCommands.JdwpListeningBeforePrompt,
    )

  def testDebugMode_NewSbtShell_StartedByRunConfiguration_JdwpListeningBeforePrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.NewShell).copy(prestartSbtShell = false),
      MockSbtProcessCommands.JdwpListeningBeforePrompt,
    )

  def testDebugMode_OldSbtShell_StartedByRunConfiguration_JdwpListeningAfterPrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.OldShell).copy(prestartSbtShell = false),
      MockSbtProcessCommands.JdwpListeningAfterPrompt,
    )

  def testDebugMode_NewSbtShell_StartedByRunConfiguration_JdwpListeningAfterPrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.NewShell).copy(prestartSbtShell = false),
      MockSbtProcessCommands.JdwpListeningAfterPrompt,
    )

  def testDebugMode_OldSbtShell_PreStartedShell_JdwpListeningGluedToPrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.OldShell),
      MockSbtProcessCommands.JdwpListeningGluedToPrompt,
    )

  def testDebugMode_NewSbtShell_PreStartedShell_JdwpListeningGluedToPrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.NewShell),
      MockSbtProcessCommands.JdwpListeningGluedToPrompt,
    )

  def testDebugMode_OldSbtShell_StartedByRunConfiguration_JdwpListeningGluedToPrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.OldShell).copy(prestartSbtShell = false),
      MockSbtProcessCommands.JdwpListeningGluedToPrompt,
    )

  def testDebugMode_NewSbtShell_StartedByRunConfiguration_JdwpListeningGluedToPrompt(): Unit =
    assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
      TestExecutionOptions(ExecutionMode.Debug, SbtProcessMode.NewShell).copy(prestartSbtShell = false),
      MockSbtProcessCommands.JdwpListeningGluedToPrompt,
    )

  // See SCL-24469
  def testDebugRunner_CannotRunApplicationConfiguration(): Unit = {
    val runManager = RunManagerImpl.getInstanceImpl(getProject)
    val factory = ApplicationConfigurationType.getInstance().getConfigurationFactories()(0)
    val settings = runManager.createConfiguration("application debug", factory)

    assertFalse(
      "SBT debug runner must not claim regular application configurations",
      new SbtDebugProgramRunner().canRun(DefaultDebugExecutor.EXECUTOR_ID, settings.getConfiguration),
    )
  }

  private def assertStoppingQueuedSbtShellRunConfigurationCancelsShellCommand(options: TestExecutionOptions): Unit = {
    val releaseFile = getTestProjectPath.resolve(s"${getTestName(false)}.release")
    try {
      ExecutionDiagnostics.clearSbtProcessOutput()
      Files.deleteIfExists(releaseFile)

      initSbtShellIfNeeded(options)
      waitUntilSbtShellIsReadyIfNeeded(options)

      // Hold the real sbt shell command queue busy so the run-configuration command is still queued when we stop it.
      val sbtShell = SbtShellCommunication.forProject(getProject)
      val blockingCommand = MockSbtProcessCommands.waitForFileCommand(releaseFile)
      val blockingCommandFuture = sbtShell.runAndCollectOutput(blockingCommand)
      waitForSbtProcessOutput(
        expectedOutput = MockSbtProcessCommands.waitingForFileOutput(releaseFile),
        timeoutMessage = "Timed out waiting for the mock SBT process to block the shell command queue"
      )

      // Start the shell-delegated run configuration; it should publish a hidden descriptor before its command runs.
      val runConfigAndSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
        getProject,
        configurationName = s"sbt cancellable command (${sbtShellModeDisplayName(options)})",
        sbtCommands = CancellableRunConfigurationCommand,
        useSbtShellInRunConfig = true,
      )
      val executionObserver = RunConfigurationExecutionObserver.subscribe(runConfigAndSettings, getTestRootDisposable)

      RunConfigInTestsExecutor.executeTopLevelConfiguration(getProject, runConfigAndSettings, options.executionMode.executor)

      // Simulate the user pressing Stop on the run configuration while the shell command is still queued.
      val handler = withExecutionDiagnostics(Some(executionObserver)) {
        executionObserver.awaitProcessStarted(timeout = 5.seconds)
      }
      ExecutionManagerImpl.stopProcess(handler)
      withExecutionDiagnostics(Some(executionObserver)) {
        executionObserver.awaitTermination(expectedExitCode = 1, timeout = 5.seconds)
      }

      // Let the blocking command finish; if Stop did not cancel the queued request, the canceled command will run next.
      releaseWaitingMockCommand(releaseFile)

      withExecutionDiagnostics(Some(executionObserver)) {
        AwaitTestUtils.waitFutureOrFail(
          blockingCommandFuture,
          10.seconds,
          "waiting for the blocking mock SBT command to finish after releasing it",
        )
        AwaitTestUtils.waitForConditionOrFail(
          5.seconds,
          "Timed out waiting for SBT shell to return to the idle state after releasing the blocking command",
        ) { () =>
          sbtShell.isRunningAndIdle
        }
      }

      // The mock SBT process must never accept the canceled run-configuration command.
      val sbtProcessOutput = ExecutionDiagnostics.sbtProcessOutputSnapshot
      val unexpectedAcceptedOutput = s"[info] mock sbt accepted: $CancellableRunConfigurationCommand"
      withExecutionDiagnostics(Some(executionObserver)) {
        assertFalse(
          s"""Stopping a shell-delegated run configuration must remove its queued SBT shell command.
             |Unexpected output:
             |${unexpectedAcceptedOutput.indent(2)}Actual SBT process output:
             |${sbtProcessOutput.indent(2)}""".stripMargin,
          sbtProcessOutput.contains(unexpectedAcceptedOutput),
        )
      }
    } finally {
      releaseWaitingMockCommand(releaseFile)
      tearDownForTestCase(options)
    }
  }

  private def assertDebugSbtShellRunConfigurationWithMockJdwpListeningOutput(
    options: TestExecutionOptions,
    command: String,
  ): Unit =
    try {
      initSbtShellIfNeeded(options)
      waitUntilSbtShellIsReadyIfNeeded(options)
      clearSbtProcessOutputDiagnostics()

      val runConfigAndSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
        getProject,
        configurationName = s"sbt $command (${sbtShellModeDisplayName(options)}, prestart=${options.prestartSbtShell})",
        sbtCommands = command,
        useSbtShellInRunConfig = true,
      )
      val executionObserver = RunConfigurationExecutionObserver.subscribe(runConfigAndSettings, getTestRootDisposable)

      RunConfigInTestsExecutor.executeTopLevelConfiguration(
        getProject,
        runConfigAndSettings,
        options.executionMode.executor,
        descriptorCallback = executionObserver.recordRunContentDescriptor,
      )
      executionObserver.awaitSuccessfulTermination(timeout = 10.seconds)
      assertExpectedDebugOutput(options, executionObserver)

      val expectedCommandOutput = MockSbtProcessCommands.jdwpListeningCommandOutput(command)
      val runConfigurationOutput = executionObserver.consoleOutputSnapshot
      assertTrue(
        s"""Run configuration console output must contain regular mock command output.
           |Expected output fragment:
           |${expectedCommandOutput.indent(2)}Actual run configuration console output:
           |${runConfigurationOutput.indent(2)}""".stripMargin,
        runConfigurationOutput.contains(expectedCommandOutput),
      )
      assertFalse(
        s"""Run configuration console output must not contain shell JDWP listening banner.
           |Unexpected output fragment:
           |${MockSbtProcessCommands.JdwpListeningMessage.indent(2)}Actual run configuration console output:
           |${runConfigurationOutput.indent(2)}""".stripMargin,
        runConfigurationOutput.contains(MockSbtProcessCommands.JdwpListeningMessage),
      )

      AwaitTestUtils.waitForConditionOrFail(
        5.seconds,
        "Timed out waiting for raw SBT process diagnostics to contain the mock JDWP listening banner",
      ) { () =>
        ExecutionDiagnostics.sbtProcessOutputSnapshot.contains(MockSbtProcessCommands.JdwpListeningMessage)
      }
    } finally {
      tearDownForTestCase(options)
    }

  private def waitForSbtProcessOutput(expectedOutput: String, timeoutMessage: String): Unit =
    withExecutionDiagnostics() {
      AwaitTestUtils.waitForConditionOrFail(5.seconds, timeoutMessage) { () =>
        ExecutionDiagnostics.sbtProcessOutputSnapshot.contains(expectedOutput)
      }
    }

  private def releaseWaitingMockCommand(releaseFile: Path): Unit =
    if (!Files.exists(releaseFile)) {
      Files.writeString(releaseFile, "release")
    }
}

private object SbtRunConfiguration_MockedProcess_ExecutionTest {
  private val CancellableRunConfigurationCommand = "cancellableRunConfigurationCommand"
}
