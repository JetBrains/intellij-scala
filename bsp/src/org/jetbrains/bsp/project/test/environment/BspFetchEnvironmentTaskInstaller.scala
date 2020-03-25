package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.{RunManager, RunManagerEx, RunManagerListener, RunnerAndConfigurationSettings}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project


class BspFetchEnvironmentTaskInstaller(project: Project) extends RunManagerListener {
  private var settingsToInit: List[RunnerAndConfigurationSettings] = Nil

  override def runConfigurationAdded(settings: RunnerAndConfigurationSettings): Unit = {
    runManagerEx.foreach(installFetchEnvironmentTask(settings, _))
  }

  private def installFetchEnvironmentTask(settings: RunnerAndConfigurationSettings, runManager: RunManagerEx): Unit = {
    val runConfiguration = settings.getConfiguration
    if (BspTesting.isBspRunnerSupportedConfiguration(runConfiguration)) {
      val tasks = runManager.getBeforeRunTasks(BspFetchTestEnvironmentTask.runTaskKey)
      if (tasks.isEmpty) {
        val beforeRunTasks = runManager.getBeforeRunTasks(runConfiguration)
        val task = new BspFetchTestEnvironmentTask
        task.setEnabled(true)
        beforeRunTasks.add(task)
      }
    }
  }

  override def stateLoaded(runManager: RunManager, isFirstLoadState: Boolean): Unit = {
    if (isFirstLoadState) {
      runManagerEx.foreach {
        runManager => settingsToInit.foreach(installFetchEnvironmentTask(_, runManager))
      }
      settingsToInit = Nil
    }
  }
  
  private def runManagerEx: Option[RunManagerEx] =
    Option(ServiceManager.getServiceIfCreated(project, classOf[RunManager]))
      .map(_.asInstanceOf[RunManagerEx])
}
