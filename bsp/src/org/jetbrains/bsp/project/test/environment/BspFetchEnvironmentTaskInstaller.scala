package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.{RunManagerEx, RunManagerListener, RunnerAndConfigurationSettings}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

final class BspFetchEnvironmentTaskInstaller(project: Project) {
  def init(): Unit = {
    val runManager = RunManagerEx.getInstanceEx(project)
    project.getMessageBus.connect().subscribe(RunManagerListener.TOPIC,
      new RunManagerListener {
        override def runConfigurationAdded(settings: RunnerAndConfigurationSettings): Unit = {
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
    )
  }
}

object BspFetchEnvironmentTaskInstaller {
  def getInstance(project: Project): BspFetchEnvironmentTaskInstaller = {
    project.getService(classOf[BspFetchEnvironmentTaskInstaller])
  }

  private final class Startup extends StartupActivity {
    override def runActivity(project: Project): Unit = {
      getInstance(project).init()
    }
  }
}
