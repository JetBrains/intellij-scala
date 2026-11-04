package org.jetbrains.sbt.runner.eventsPublishing

import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.RunManagerImpl
import junit.framework.TestCase.assertFalse
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}
import org.jetbrains.sbt.runner.{SbtDebugProgramRunner, TestExecutionOptions}

class SbtRunConfiguration_ExecutionEventsPublishingTest_DebugMode extends SbtRunConfiguration_ExecutionEventsPublishingTestBase {
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

  def testDebugMode_OldSbtShellStartedByRunConfiguration_PublishesCompleteExecutionLifecycle(): Unit =
    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.Shell(isNewShell = false),
    ).copy(prestartSbtShell = false))

  def testDebugMode_NewSbtShellStartedByRunConfiguration_PublishesCompleteExecutionLifecycle(): Unit =
    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Debug,
      SbtProcessMode.Shell(isNewShell = true),
    ).copy(prestartSbtShell = false))

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
} 