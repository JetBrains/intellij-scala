package org.jetbrains.plugins.cbt.runner.internal

import java.util

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations._
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.RunCbtDebuggerBeforeRunTask

import scala.collection.JavaConversions._


class CbtDebugConfiguration(val task: String,
                            val project: Project,
                            val configurationFactory: ConfigurationFactory)
  extends RemoteConfiguration(project, configurationFactory) {
  PORT = "5005"
  HOST = "localhost"
  SERVER_MODE = false
  USE_SOCKET_TRANSPORT = true

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    val beforeRunTask = new RunCbtDebuggerBeforeRunTask(task, project.getBaseDir.getPath)
    List(beforeRunTask)
  }
}
