package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.TaskModuleData

class CbtDebugConfigurationFactory(task: String,
                                   taskModuleData: TaskModuleData,
                                   configType: ConfigurationType)
  extends ConfigurationFactory(configType) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtDebugConfiguration(task, taskModuleData, project, this)
}
