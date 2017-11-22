package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.Executor
import com.intellij.execution.configurations.{ConfigurationFactory, RunConfiguration, RunConfigurationBase, RunProfileState}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import org.jetbrains.plugins.cbt.runner.{CbtCommandLineState, CbtTask}

class CbtTaskConfiguration(task: CbtTask, configurationFactory: ConfigurationFactory)
  extends RunConfigurationBase(task.project, configurationFactory, s"CBT ${task.name}") {
  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = null

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    new CbtCommandLineState(task, environment)
}
