package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

class CbtDebugConfigurationFactory(typez: ConfigurationType) extends ConfigurationFactory(typez) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtDebugConfiguration(project, this)
}
