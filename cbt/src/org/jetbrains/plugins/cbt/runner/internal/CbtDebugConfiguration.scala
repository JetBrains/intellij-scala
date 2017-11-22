package org.jetbrains.plugins.cbt.runner.internal

import java.util

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations._
import com.intellij.execution.remote.RemoteConfiguration
import org.jetbrains.plugins.cbt.runner.{CbtTask, RunCbtDebuggerBeforeRunTask}

import scala.collection.JavaConversions._


class CbtDebugConfiguration(task: CbtTask, configurationFactory: ConfigurationFactory)
  extends RemoteConfiguration(task.project, configurationFactory) {
  private val module = task.moduleOpt.get
  setModule(module)
  setName(s"Debug ${module.getName}: ${task.name}")

  PORT = "5005"
  HOST = "localhost"
  SERVER_MODE = false
  USE_SOCKET_TRANSPORT = true

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    val beforeRunTask = new RunCbtDebuggerBeforeRunTask(task)
    List(beforeRunTask)
  }
}
