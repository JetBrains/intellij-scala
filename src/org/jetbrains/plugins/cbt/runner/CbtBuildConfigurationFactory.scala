package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

class CbtBuildConfigurationFactory(task: String,
                                   workingDir: String,
                                   typez: ConfigurationType,
                                   callback: Option[() => Unit] = None) extends ConfigurationFactory(typez) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtBuildConfiguration(task, workingDir, project, callback, this)
}

