package org.jetbrains.sbt.runner.utils

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runToolbar.RunToolbarProcessData
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.{Executor, ProgramRunnerUtil, RunnerAndConfigurationSettings}
import com.intellij.openapi.project.Project

object RunConfigInTestsExecutor {

  def executeTopLevelConfiguration(
    project: Project,
    settings: RunnerAndConfigurationSettings,
    executor: Executor,
    descriptorCallback: RunContentDescriptor => Unit = _ => (),
  ): Unit = {
    val environment = ExecutionEnvironmentBuilder
      .create(executor, settings)
      .activeTarget()
      .build()
    environment.putUserData(RunToolbarProcessData.RW_MAIN_CONFIGURATION_ID, settings.getUniqueID)

    // In unit-test mode ExecutionManagerImpl normally skips compileAndRun and starts the runner directly.
    // Enabling this flag keeps the production before-launch pipeline active, so configured Build/Make tasks are really run (when actual).
    ExecutionManagerImpl.getInstance(project).setForceCompilationInTests(true)

    // The most top-level API to run a configuration that I found
    ProgramRunnerUtil.executeConfigurationAsync(
      environment,
      false,
      true,
      (descriptor: RunContentDescriptor) => {
        descriptorCallback(descriptor)
      },
    )
  }
}
