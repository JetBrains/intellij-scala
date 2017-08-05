package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.CbtProcessListener

class CbtBuildConfigurationFactory(task: String,
                                   useDirect: Boolean,
                                   module: Module,
                                   options: Seq[String],
                                   typez: ConfigurationType,
                                   listener: CbtProcessListener) extends ConfigurationFactory(typez) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtBuildConfiguration(task, useDirect, module, options, project, listener, this)
}
