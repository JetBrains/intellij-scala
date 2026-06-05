package org.jetbrains.sbt.runner.beforeLaunch

import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}
import org.jetbrains.sbt.runner.beforeLaunch.utils.{CompileStepBeforeRunTestUtil, CompileStepBeforeRunTracker}
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
class SbtRunConfiguration_ExecutionTest_Mocked_BuildBeforeLaunch extends SbtRunConfiguration_MockedProcess_ExecutionTestBase {

  def testRunMode_NoSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfigurationDoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NoShell,
    ))

  def testRunMode_OldSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfigurationDoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.OldShell,
    ))

  def testRunMode_NewSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfigurationDoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NewShell,
    ))

  def testDebugMode_NoSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfigurationDoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NoShell,
    ))

  def testDebugMode_OldSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfigurationDoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.OldShell,
    ))

  def testDebugMode_NewSbtShell_FreshSbtRunConfigurationDoesNotRunBuild(): Unit =
    assertFreshSbtRunConfigurationDoesNotRunBuild(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NewShell,
    ))

  def testRunMode_NoSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NoShell,
    ))

  def testRunMode_OldSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.OldShell,
    ))

  def testRunMode_NewSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.NewShell,
    ))

  def testDebugMode_NoSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NoShell,
    ))

  def testDebugMode_OldSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.OldShell,
    ))

  def testDebugMode_NewSbtShell_FreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(): Unit =
    assertFreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.NewShell,
    ))

  private def assertFreshSbtRunConfigurationDoesNotRunBuild(options: TestExecutionOptions): Unit = {
    val buildTracker = runFreshSbtRunConfiguration_BuildBeforeLaunch(options) { _ =>
      ()
    }
    assertCompileStepBeforeRunWasNotExecuted(buildTracker)
  }

  private def assertFreshSbtRunConfigurationRunsExplicitBuildBeforeLaunch(options: TestExecutionOptions): Unit = {
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
    RunConfigInTestsExecutor.executeTopLevelConfiguration(getProject, runnerAndConfigSettings, options.executionMode.executor)
    // We use small timeout because the run configuration starts a lightweight mock JVM instead of a real sbt process.
    executionObserver.awaitSuccessfulTermination(timeout = 5.seconds)

    buildTracker
  }

  private def observeExecution(settings: RunnerAndConfigurationSettings): RunConfigurationExecutionObserver =
    RunConfigurationExecutionObserver.subscribe(settings, getTestRootDisposable)
}
