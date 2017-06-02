package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

class CbtRunConfigurationFactory(typez: ConfigurationType) extends ConfigurationFactory(typez){
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtRunConfiguration(project, this, "")

}
