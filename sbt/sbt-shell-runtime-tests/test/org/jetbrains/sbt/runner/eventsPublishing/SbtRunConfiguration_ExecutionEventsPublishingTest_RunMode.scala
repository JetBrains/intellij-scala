package org.jetbrains.sbt.runner.eventsPublishing

import com.intellij.util.system.OS
import org.jetbrains.sbt.runner.TestExecutionOptions
import org.jetbrains.sbt.runner.TestExecutionOptions.{ExecutionMode, SbtProcessMode}

class SbtRunConfiguration_ExecutionEventsPublishingTest_RunMode extends SbtRunConfiguration_ExecutionEventsPublishingTestBase {
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

  def testRunMode_NewSbtShell_PublishesCompleteExecutionLifecycle(): Unit = {
    // TODO: SCL-26340 fails on Windows
    if (OS.CURRENT == OS.Windows) return

    assertSbtRunConfiguration_PublishesCompleteExecutionLifecycle(TestExecutionOptions(
      ExecutionMode.Run,
      SbtProcessMode.Shell(isNewShell = true),
    ))
  }
} 