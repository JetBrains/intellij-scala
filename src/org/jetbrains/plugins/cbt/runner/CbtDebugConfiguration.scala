package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations._
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.openapi.project.Project

import scala.collection.JavaConversions._


class CbtDebugConfiguration(val project: Project,
//                            val task: String,
//                            val workingDir: String,
                            val configurationFactory: ConfigurationFactory)
  extends RemoteConfiguration(project, configurationFactory) {

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    val beforeRunTask = new RunCbtDebuggerBeforeRunTask("run", project.getBaseDir.getPath)
    List(beforeRunTask)
  }
}
