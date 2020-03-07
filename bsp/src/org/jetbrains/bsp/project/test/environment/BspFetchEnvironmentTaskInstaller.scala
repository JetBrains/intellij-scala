package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.{RunManager, RunManagerEx, RunManagerListener, RunnerAndConfigurationSettings}

class BspFetchEnvironmentTaskInstaller extends RunManagerListener {
  private var runManagerOpt: Option[RunManagerEx] = None

  // temp solution to SCL-17155 to avoid cyclid dependencies
  // some configurations are added during RunManager initialization
  private var settingsToInit: List[RunnerAndConfigurationSettings] = Nil

  override def runConfigurationAdded(settings: RunnerAndConfigurationSettings): Unit = {
    val runManager = runManagerOpt.getOrElse {
      settingsToInit ::= settings
      return
    }

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

  override def stateLoaded(runManager: RunManager, isFirstLoadState: Boolean): Unit = {
    if (isFirstLoadState) {
      this.runManagerOpt = Some(runManager.asInstanceOf[RunManagerEx])
      settingsToInit.foreach(runConfigurationAdded)
      settingsToInit = Nil
    }
  }
}
