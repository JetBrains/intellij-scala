package org.jetbrains.plugins.cbt.runner.internal

import java.util

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations._
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.RunCbtDebuggerBeforeRunTask

import scala.collection.JavaConversions._


class CbtDebugConfiguration(task: String,
                            module: Module,
                            project: Project,
                            configurationFactory: ConfigurationFactory)
  extends RemoteConfiguration(project, configurationFactory) {
  setModule(module)
  setName(s"Debug ${module.getName}: $task")

  PORT = "5005"
  HOST = "localhost"
  SERVER_MODE = false
  USE_SOCKET_TRANSPORT = true

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    val beforeRunTask = new RunCbtDebuggerBeforeRunTask(task, module)
    List(beforeRunTask)
  }
}
