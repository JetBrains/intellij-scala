package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

class CbtBuildConfigurationFactory(task: String,
                                   typez: ConfigurationType,
                                   debug: Boolean = false,
                                   callback: Option[() => Unit] = None) extends ConfigurationFactory(typez) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtBuildConfiguration(task, project, debug, callback, this)
}

