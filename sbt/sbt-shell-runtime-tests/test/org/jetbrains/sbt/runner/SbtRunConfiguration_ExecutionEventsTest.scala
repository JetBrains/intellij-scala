package org.jetbrains.sbt.runner

import com.intellij.execution.ExecutionManager
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.{RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}
import org.jetbrains.sbt.runner.utils.{ExecutionEventsCollector, RunConfigInTestsExecutor, RunConfigurationExecutionObserver, SbtRunConfigurationTestFactory}
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellTestUtil}
import org.junit.Assert.{assertFalse, assertSame, assertTrue}

import scala.concurrent.duration.DurationInt

/**
 * Verifies that SBT run configurations publish the complete IntelliJ execution lifecycle through [[ExecutionManager.EXECUTION_TOPIC]].
 *
 * SBT shell execution does not start a dedicated OS process for every run configuration,
 * so this test protects the synthetic process-handler path used by shell delegation.
 *
 * The same event contract is checked for run and debug executors, with no shell, the legacy SBT shell, and the terminal-based SBT shell.
 *
 * Related tickets:
 *  - [[https://youtrack.jetbrains.com/issue/SCL-24434 SCL-24434]]
 *  - [[https://youtrack.jetbrains.com/issue/SCL-22453 SCL-22453]]
 */
class SbtRunConfiguration_ExecutionEventsTest extends SbtRunConfiguration_WithMockSbtProcess_TestBase {

  def testRunMode_NoSbtShell_PublishesCompleteExecutionLifecycle(): Unit =
    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NoShell,
    ))

  def testRunMode_OldSbtShell_PublishesCompleteExecutionLifecycle(): Unit =
    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.Shell(isNewShell = false),
    ))

  def testRunMode_NewSbtShell_PublishesCompleteExecutionLifecycle(): Unit =
    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.Shell(isNewShell = true),
    ))

  def testDebugMode_NoSbtShell_PublishesCompleteExecutionLifecycle(): Unit =
    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NoShell,
    ))

  def testDebugMode_OldSbtShell_PublishesCompleteExecutionLifecycle(): Unit =
    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.Shell(isNewShell = false),
    ))

  def testDebugMode_NewSbtShell_PublishesCompleteExecutionLifecycle(): Unit =
    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.Shell(isNewShell = true),
    ))

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

  private def assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(options: TestExecutionOptions): Unit = {
    try {
      assertSbtRunConfiguration_PublishesCompleteExecutionLifecycleInner(options)
    } finally {
      tearDownForTestCase(options)
    }
  }

  private def assertSbtRunConfiguration_PublishesCompleteExecutionLifecycleInner(options: TestExecutionOptions): Unit = {
    val runConfigAndSettings: RunnerAndConfigurationSettingsImpl = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
      getProject,
      configurationName = s"sbt compile (${options.executionMode.displayName}, ${sbtShellModeDisplayName(options)})",
      sbtCommands = "compile",
      useSbtShellInRunConfig = options.useSbtShellInRunConfig,
    )

    val executionObserver = new RunConfigurationExecutionObserver(runConfigAndSettings)
    initSbtShellIfNeeded(options)
    waitUntilSbtShellIsReadyIfNeeded(options)

    val eventsCollector = new ExecutionEventsCollector(runConfigAndSettings)

    val connection = getProject.getMessageBus.connect(getTestRootDisposable)
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, executionObserver)
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, eventsCollector)

    RunConfigInTestsExecutor.executeTopLevelConfiguration(getProject, runConfigAndSettings, options.executionMode.executor)
    executionObserver.awaitSuccessfulTermination(timeout = 10.seconds)

    val actualEvents = eventsCollector.eventsSnapshot
    assertCollectionEquals(
      Seq(
        "processStartScheduled",
        "processStarting",
        "processStartingWithHandler",
        "processStarted",
        "processTerminating",
        "processTerminated"
      ),
      actualEvents.map(_.name)
    )

    val processHandlers = actualEvents.flatMap(_.processHandler)
    assertTrue("Expected lifecycle events with a process handler", processHandlers.nonEmpty)
    processHandlers.foreach(assertSame("Expected all handler-specific lifecycle events to use the same handler", processHandlers.head, _))
  }

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

      val executionObserver = new RunConfigurationExecutionObserver(runConfigAndSettings)
      getProject.getMessageBus.connect(getTestRootDisposable).subscribe(ExecutionManager.EXECUTION_TOPIC, executionObserver)

      RunConfigInTestsExecutor.executeTopLevelConfiguration(getProject, runConfigAndSettings, options.executionMode.executor)

      executionObserver.awaitFailedToStart(
        expectedCauseMessage = SbtBundle.message("debugging.for.sbt.shell.is.disabled.in.sbt.settings"),
        timeout = 10.seconds,
      )
    } finally {
      tearDownForTestCase(options)
    }

  private def tearDownForTestCase(options: TestExecutionOptions): Unit = {
    if (options.useSbtShellInRunConfig) {
      SbtProcessManager.forProject(getProject).destroyProcess()
    }
  }

  private def waitUntilSbtShellIsReadyIfNeeded(options: TestExecutionOptions): Unit =
    if (options.useSbtShellInRunConfig) {
      SbtShellTestUtil.waitUntilSbtShellIsReady(getProject, 5.seconds, "Can't start sbt shell")
    }
}
