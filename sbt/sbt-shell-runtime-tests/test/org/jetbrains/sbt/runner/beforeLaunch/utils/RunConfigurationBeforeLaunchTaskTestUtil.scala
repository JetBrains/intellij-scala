package org.jetbrains.sbt.runner.beforeLaunch.utils

import com.intellij.execution.impl.{RunConfigurationBeforeRunProvider, RunManagerImpl}
import com.intellij.execution.{BeforeRunTask, BeforeRunTaskProvider, RunnerAndConfigurationSettings}
import com.intellij.openapi.project.Project
import org.junit.Assert.assertNotNull

private[runner] object RunConfigurationBeforeLaunchTaskTestUtil {

  def addRunConfigurationBeforeLaunchTask(
    project: Project,
    parentSettings: RunnerAndConfigurationSettings,
    childSettings: RunnerAndConfigurationSettings,
  ): Unit = {
    val parentConfiguration = parentSettings.getConfiguration
    val provider = BeforeRunTaskProvider
      .getProvider(project, RunConfigurationBeforeRunProvider.ID)
      .asInstanceOf[RunConfigurationBeforeRunProvider]
    assertNotNull("The Run another configuration before-launch provider must be available", provider)

    val task = provider.createTask(parentConfiguration)
    task.setSettingsWithTarget(childSettings, null)
    task.setEnabled(true)

    val runManager = RunManagerImpl.getInstanceImpl(project)
    val tasks = new java.util.ArrayList[BeforeRunTask[?]](runManager.getBeforeRunTasks(parentConfiguration))
    tasks.add(task)
    runManager.setBeforeRunTasks(parentConfiguration, tasks)
  }
}
