package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.{RunManagerEx, RunManagerListener, RunnerAndConfigurationSettings}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project

class BspFetchEnvironmentTaskInstaller(project: Project) extends ProjectComponent {
  override def projectOpened(): Unit = {
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
