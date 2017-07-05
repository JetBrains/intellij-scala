package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

class CbtBuildConfigurationFactory(typez: ConfigurationType)  extends ConfigurationFactory(typez){
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtBuildConfiguration(project, this)
}

