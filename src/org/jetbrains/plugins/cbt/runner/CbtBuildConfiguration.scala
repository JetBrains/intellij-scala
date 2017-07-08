package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.execution.configurations._
import com.intellij.execution.impl.UnknownBeforeRunTaskProvider
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{BeforeRunTask, Executor}
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

import scala.collection.JavaConversions._


class CbtBuildConfiguration(val task: String,
                            val project: Project,
                            val callback: Option[() => Unit],
                            val configurationFactory: ConfigurationFactory)
  extends ModuleBasedConfiguration[RunConfigurationModule](task.capitalize, new RunConfigurationModule(project), configurationFactory) {

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    // For not adding default buildTask
    val unknownTask = new UnknownBeforeRunTaskProvider("unknown").createTask(this)
    List(unknownTask)
  }

  override def getValidModules: util.Collection[Module] = List()

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = null

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    new CbtComandLineState(task, project.getBaseDir.getPath, callback, environment)
}
