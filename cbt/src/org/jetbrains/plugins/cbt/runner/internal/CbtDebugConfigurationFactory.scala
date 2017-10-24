package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

class CbtDebugConfigurationFactory(task: String,
                                   module: Module,
                                   configType: ConfigurationType)
  extends ConfigurationFactory(configType) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtDebugConfiguration(task, module, project, this)
}
