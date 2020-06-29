package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.{RunManager, RunManagerEx, RunManagerListener, RunnerAndConfigurationSettings}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project


class BspFetchTestEnvironmentTaskInstaller(project: Project) extends RunManagerListener {
  var logger = Logger.getInstance(classOf[BspFetchTestEnvironmentTaskInstaller])

  private var settingsToInit: List[RunnerAndConfigurationSettings] = Nil
  private var stateLoaded = false

  override def runConfigurationAdded(settings: RunnerAndConfigurationSettings): Unit =  try {
    runManagerEx match {
      case Some(runManager) =>
        installFetchEnvironmentTask(settings, runManager)
      case None if !stateLoaded =>
        settingsToInit ::= settings
        logger.info(s"Adding '${settings.getName}' config to BspFetchTestEnvironmentTaskInstaller queue")
      case _ =>
        logger.error("Run manager is null after initialization")
    }
  } catch {
    case e: Throwable => logger.error(e)
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
      stateLoaded = true
      logger.info("RunConfigurations from queue updated")
    }
  
  private def runManagerEx: Option[RunManagerEx] =
    Option(ServiceManager.getServiceIfCreated(project, classOf[RunManager]))
      .map(_.asInstanceOf[RunManagerEx])
}
