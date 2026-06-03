package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.{RunManager, RunManagerEx, RunManagerListener, RunnerAndConfigurationSettings}
import com.intellij.openapi.project.Project

class BspFetchTestEnvironmentTaskInstaller(project: Project) extends RunManagerListener {
  private var settingsToInit: List[RunnerAndConfigurationSettings] = Nil

  override def runConfigurationAdded(settings: RunnerAndConfigurationSettings): Unit = {
    runManagerEx match {
      case Some(runManager) =>
        installFetchEnvironmentTask(settings, runManager)
      case None =>
        settingsToInit ::= settings
    }
  }

  private def installFetchEnvironmentTask(settings: RunnerAndConfigurationSettings, runManager: RunManagerEx): Unit = {
    val runConfiguration = settings.getConfiguration
    if (BspEnvironmentRunnerExtension.isSupported(runConfiguration)) {
      val tasks = runManager.getBeforeRunTasks(runConfiguration, BspFetchEnvironmentTask.runTaskKey)
      if (tasks.isEmpty) {
        val task = new BspFetchEnvironmentTask
        task.setEnabled(true)

        val mutableRunTasks = new java.util.ArrayList(runManager.getBeforeRunTasks(runConfiguration))
        mutableRunTasks.add(task)
        runManager.setBeforeRunTasks(runConfiguration, mutableRunTasks)
      }
    }
  }

  override def stateLoaded(runManager: RunManager, isFirstLoadState: Boolean): Unit =
    if (isFirstLoadState && runManager.isInstanceOf[RunManagerEx]) {
        settingsToInit.foreach(installFetchEnvironmentTask(_, runManager.asInstanceOf[RunManagerEx]))
        settingsToInit = Nil
    }
  
  private def runManagerEx: Option[RunManagerEx] =
    Option(project.getServiceIfCreated(classOf[RunManager]))
      .map(_.asInstanceOf[RunManagerEx])
}
