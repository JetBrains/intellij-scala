package org.jetbrains.plugins.cbt.runner.internal

import java.util
import java.util.Collections

import com.intellij.execution.configurations._
import com.intellij.execution.impl.UnknownBeforeRunTaskProvider
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{BeforeRunTask, Executor}
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.{CbtCommandLineState, CbtProcessListener, CbtTask}
import org.jetbrains.plugins.cbt._


class CbtModuleTaskConfiguration(taskData: CbtTask, configurationFactory: ConfigurationFactory)
  extends ModuleBasedConfiguration[RunConfigurationModule](s"${taskData.moduleOpt.get.getName}: ${taskData.name}",
    new RunConfigurationModule(taskData.project), configurationFactory) {
  setModule(taskData.moduleOpt.get)

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    // For not adding default buildTask
    val unknownTask = new UnknownBeforeRunTaskProvider("unknown").createTask(this)
    Collections.singletonList(unknownTask)
  }

  override def getValidModules: util.Collection[Module] = Collections.emptyList()

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = null

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    new CbtCommandLineState(taskData, environment)
}
