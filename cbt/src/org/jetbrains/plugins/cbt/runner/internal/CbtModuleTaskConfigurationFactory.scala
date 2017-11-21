package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.CbtTask

class CbtModuleTaskConfigurationFactory(task: CbtTask, configurationType: ConfigurationType)
  extends ConfigurationFactory(configurationType) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtModuleTaskConfiguration(task, this)
}
