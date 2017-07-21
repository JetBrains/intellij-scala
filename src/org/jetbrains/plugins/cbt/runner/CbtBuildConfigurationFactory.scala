package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

class CbtBuildConfigurationFactory(task: String,
                                   useDirect: Boolean,
                                   workingDir: String,
                                   typez: ConfigurationType,
                                   callback: Option[() => Unit] = None) extends ConfigurationFactory(typez) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtBuildConfiguration(task, useDirect, workingDir, project, callback, this)
}

