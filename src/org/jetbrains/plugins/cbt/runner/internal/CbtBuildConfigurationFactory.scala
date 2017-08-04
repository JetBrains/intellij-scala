package org.jetbrains.plugins.cbt.runner.internal

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt.runner.{CbtProcessListener, TaskModuleData}

class CbtBuildConfigurationFactory(task: String,
                                   useDirect: Boolean,
                                   taskModuleData: TaskModuleData,                                   options: Seq[String],
                                   typez: ConfigurationType,
                                   listener: CbtProcessListener) extends ConfigurationFactory(typez) {
  override def createTemplateConfiguration(project: Project): RunConfiguration =
    new CbtBuildConfiguration(task, useDirect, taskModuleData, options, project, listener, this)
}
