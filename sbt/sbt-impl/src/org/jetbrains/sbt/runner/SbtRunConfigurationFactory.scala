package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

/**
 * Configuration factory for [[SbtConfigurationType]]
 * @param typez type of configuration supported by this factory.
 */
class SbtRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez) {

  //should be equal to the default value of SbtConfigurationType.getDisplayName
  override def getId: String = "sbt Task"

  override def createTemplateConfiguration(project: Project): RunConfiguration = new SbtRunConfiguration(project, this, "")
}