package org.jetbrains.sbt.runner.eventsPublishing

import com.intellij.execution.ExecutionManager
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.runner.utils.{ExecutionEventsCollector, RunConfigInTestsExecutor, RunConfigurationExecutionObserver, SbtRunConfigurationTestFactory}
import org.jetbrains.sbt.runner.{SbtRunConfiguration_MockedProcess_ExecutionTestBase, TestExecutionOptions}
import org.junit.Assert.{assertSame, assertTrue}

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
abstract class SbtRunConfiguration_ExecutionEventsPublishingTestBase extends SbtRunConfiguration_MockedProcess_ExecutionTestBase {

  protected def assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(options: TestExecutionOptions): Unit = {
    try {
      assertSbtRunConfiguration_PublishesCompleteExecutionLifecycleInner(options)
    } finally {
      tearDownForTestCase(options)
    }
  }

  protected def assertSbtRunConfiguration_PublishesCompleteExecutionLifecycleInner(options: TestExecutionOptions): Unit = {
    val runConfigAndSettings: RunnerAndConfigurationSettingsImpl = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
      getProject,
      configurationName = s"sbt compile (${options.executionMode.displayName}, ${sbtShellModeDisplayName(options)})",
      sbtCommands = "compile",
      useSbtShellInRunConfig = options.useSbtShellInRunConfig,
    )

    initSbtShellIfNeeded(options)
    waitUntilSbtShellIsReadyIfNeeded(options)

    val executionObserver = RunConfigurationExecutionObserver.subscribe(runConfigAndSettings, getTestRootDisposable)
    val eventsCollector = new ExecutionEventsCollector(runConfigAndSettings)

    val connection = getProject.getMessageBus.connect(getTestRootDisposable)
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, eventsCollector)

    clearSbtProcessOutputDiagnostics()
    RunConfigInTestsExecutor.executeTopLevelConfiguration(
      getProject,
      runConfigAndSettings,
      options.executionMode.executor,
      descriptorCallback = executionObserver.recordRunContentDescriptor,
    )
    executionObserver.awaitSuccessfulTermination(timeout = 10.seconds)
    assertExpectedDebugOutput(options, executionObserver)

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
}
