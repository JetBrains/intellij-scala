package org.jetbrains.sbt.runner.beforeLaunch

import com.intellij.debugger.jvm.advanced.java.log.capture.LogCapture
import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.plugins.scala.util.CollectingLoggedMessagesProcessor
import org.jetbrains.sbt.runner.TestExecutionOptions.ExecutionMode
import org.jetbrains.sbt.runner.beforeLaunch.utils.{CompileStepBeforeRunTestUtil, CompileStepBeforeRunTracker, DebuggerSessionsAwaiter}
import org.jetbrains.sbt.runner.utils.{RunConfigInTestsExecutor, RunConfigurationExecutionObserver, SbtRunConfigurationTestFactory}
import org.jetbrains.sbt.runner.{SbtRunConfiguration, SbtRunConfiguration_MockedProcess_ExecutionTestBase, TestExecutionOptions}
import org.junit.Assert.assertEquals

import scala.concurrent.duration.DurationInt

/**
 * Verifies the IntelliJ Build before-launch contract for fresh SBT task run configurations.
 *
 * This suite covers Run and Debug execution for configurations created directly from the SBT run configuration factory.
 *
 * For a fresh configuration it asserts that the IDE does not add or execute the Build / Make before-launch step by default.
 * Both with sbt shell enabled and with sbt shell disabled.
 * This guards the default SBT run/debug path from accidentally invoking IntelliJ Build before the SBT task starts.
 *
 * The suite also covers the explicit user-added Build before-launch case.
 * Those tests add `CompileStepBeforeRun` to the fresh configuration and assert that the before-launch step is invoked before the SBT runner proceeds.
 * The positive and negative assertions use the same signal: a `ProjectTaskListener` event whose build originator is `CompileStepBeforeRun`.
 *
 * Related tickets:
 *  - [[https://youtrack.jetbrains.com/issue/SCL-24434 SCL-24434]]
 *  - [[https://youtrack.jetbrains.com/issue/SCL-25433 SCL-25433]]
 */
abstract class SbtRunConfiguration_BuildBeforeLaunch_TestBase extends SbtRunConfiguration_MockedProcess_ExecutionTestBase {

  protected def assertFreshSbtRunConfiguration_DoesNotRunBuild(options: TestExecutionOptions): Unit = {
    val buildTracker = runFreshSbtRunConfiguration_BuildBeforeLaunch(options) { _ =>
      ()
    }
    assertCompileStepBeforeRunWasNotExecuted(buildTracker)
  }

  protected def assertFreshSbtRunConfiguration_RunsExplicitBuildBeforeLaunch(options: TestExecutionOptions): Unit = {
    val buildTracker = runFreshSbtRunConfiguration_BuildBeforeLaunch(options) { configuration =>
      CompileStepBeforeRunTestUtil.addCompileStepBeforeRunTask(getProject, configuration)
    }
    assertCompileStepBeforeRunWasExecuted(buildTracker)
  }

  private def assertCompileStepBeforeRunWasNotExecuted(buildTracker: CompileStepBeforeRunTracker): Unit =
    assertEquals("Build before launch must not be executed", 0, buildTracker.startedBuildCount)

  private def assertCompileStepBeforeRunWasExecuted(buildTracker: CompileStepBeforeRunTracker): Unit =
    assertEquals("Build before launch must be executed once", 1, buildTracker.startedBuildCount)

  private def runFreshSbtRunConfiguration_BuildBeforeLaunch(
    options: TestExecutionOptions
  )(
    configureBeforeRunTasks: SbtRunConfiguration => Unit
  ): CompileStepBeforeRunTracker = {
    initSbtShellIfNeeded(options)

    // Add this assertion to ensure that the provider is available before the test starts and the "Build" before launch step is not present not because the provider is missing
    CompileStepBeforeRunTestUtil.assertCompileStepBeforeRunProviderIsAvailable(getProject)

    val runnerAndConfigSettings = SbtRunConfigurationTestFactory.createNewSbtTaskRunConfiguration(
      getProject,
      s"sbt compile (${options.executionMode.displayName}, ${sbtShellModeDisplayName(options)})",
      "compile",
      options.useSbtShellInRunConfig,
    )

    val configuration = runnerAndConfigSettings.getConfiguration.asInstanceOf[SbtRunConfiguration]
    CompileStepBeforeRunTestUtil.assertCompileStepBeforeRunTasksSize(
      getProject, configuration,
      "Fresh SBT task run configurations must not have Build before launch enabled",
      0,
    )
    configureBeforeRunTasks(configuration)

    val buildTracker = new CompileStepBeforeRunTracker(getProject, getTestRootDisposable)

    val executionObserver = observeExecution(runnerAndConfigSettings)
    val debuggerSessionsAwaiter = observeDebuggerSessionsIfNeeded(options)
    clearSbtProcessOutputDiagnostics()
    assertNoLogCaptureWarningsLogged {
      RunConfigInTestsExecutor.executeTopLevelConfiguration(
        getProject,
        runnerAndConfigSettings,
        options.executionMode.executor,
        descriptorCallback = executionObserver.recordRunContentDescriptor,
      )
      // We use small timeout because the run configuration starts a lightweight mock JVM instead of a real sbt process.
      executionObserver.awaitSuccessfulTermination(timeout = 10.seconds)
      debuggerSessionsAwaiter.foreach(_.awaitAllSessionsDetached())
    }
    assertExpectedDebugOutput(options, executionObserver)

    buildTracker
  }

  private def observeExecution(settings: RunnerAndConfigurationSettings): RunConfigurationExecutionObserver =
    RunConfigurationExecutionObserver.subscribe(settings, getTestRootDisposable)

  private def observeDebuggerSessionsIfNeeded(options: TestExecutionOptions): Option[DebuggerSessionsAwaiter] =
    options.executionMode match {
      case ExecutionMode.Debug => Some(DebuggerSessionsAwaiter.subscribe(getProject, timeout = 10.seconds))
      case ExecutionMode.Run => None
    }

  private def assertNoLogCaptureWarningsLogged(body: => Unit): Unit = {
    val logCaptureLoggerName = classOf[LogCapture].getName
    val (_, loggedMessages) = CollectingLoggedMessagesProcessor.collectErrorsAndWarnings(body)
    val warnings = loggedMessages.warnings
      .filter(_.category.contains(logCaptureLoggerName))
      .map(_.message)
    assertEquals(
      s"Unexpected warnings logged by $logCaptureLoggerName: ${warnings.mkString("[", ", ", "]")}",
      0,
      warnings.size
    )
  }
}
