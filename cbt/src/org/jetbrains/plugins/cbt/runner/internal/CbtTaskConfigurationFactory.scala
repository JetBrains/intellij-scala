package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.CbtTask

class CbtTaskConfigurationFactory(task: CbtTask, confType: ConfigurationType)
  extends ConfigurationFactory(confType) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtTaskConfiguration(task.copy(project = project), this)
}

