package org.jetbrains.plugins.cbt.runner.internal

import java.util

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations._
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.{RunCbtDebuggerBeforeRunTask, TaskModuleData}

import scala.collection.JavaConversions._


class CbtDebugConfiguration(task: String,
                            taskModuleData: TaskModuleData, val project: Project,
                            configurationFactory: ConfigurationFactory)
  extends RemoteConfiguration(project, configurationFactory) {
  PORT = "5005"
  HOST = "localhost"
  SERVER_MODE = false
  USE_SOCKET_TRANSPORT = true

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    val beforeRunTask = new RunCbtDebuggerBeforeRunTask(task, taskModuleData)
    List(beforeRunTask)
  }
}
