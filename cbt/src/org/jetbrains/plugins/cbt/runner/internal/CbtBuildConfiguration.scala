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
import org.jetbrains.plugins.cbt.runner.{CbtCommandLineState, CbtProcessListener}
import org.jetbrains.plugins.cbt._


class CbtBuildConfiguration(val task: String,
                            val useDirect: Boolean,
                            val module: Module,
                            val options: Seq[String],
                            val project: Project,
                            val listener: CbtProcessListener,
                            val configurationFactory: ConfigurationFactory)
  extends ModuleBasedConfiguration[RunConfigurationModule](s"${module.getName}: $task",
    new RunConfigurationModule(project), configurationFactory) {
  setModule(module)

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    // For not adding default buildTask
    val unknownTask = new UnknownBeforeRunTaskProvider("unknown").createTask(this)
    Collections.singletonList(unknownTask)
  }

  override def getValidModules: util.Collection[Module] = Collections.emptyList()

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = null

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    new CbtCommandLineState(task, useDirect, module.baseDir, listener, environment, options)
}
