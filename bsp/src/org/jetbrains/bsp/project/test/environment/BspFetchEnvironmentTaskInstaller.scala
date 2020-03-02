package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.{RunManagerEx, RunManagerListener, RunnerAndConfigurationSettings}

class BspFetchEnvironmentTaskInstaller extends RunManagerListener {
  override def runConfigurationAdded(settings: RunnerAndConfigurationSettings): Unit = {
    val runManager = RunManagerEx.getInstanceEx(settings.getConfiguration.getProject)
    val runConfiguration = settings.getConfiguration
    if (BspTesting.isBspRunnerSupportedConfiguration(runConfiguration)) {
      val beforeRunTasks = runManager.getBeforeRunTasks(runConfiguration)
      val task = new BspFetchTestEnvironmentTask
      task.setEnabled(true)
      beforeRunTasks.add(task)
      val tasks = runManager.getBeforeRunTasks(BspFetchTestEnvironmentTask.runTaskKey)
      if (tasks.isEmpty) {
        runManager.setBeforeRunTasks(runConfiguration, beforeRunTasks)
      }
    }
  }
}
