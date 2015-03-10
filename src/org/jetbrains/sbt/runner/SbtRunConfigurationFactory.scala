package org.jetbrains.sbt.runner

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

/**
 * Configuration factory for [[SbtConfigurationType]]
 * @param typez type of configuration supported by this factory.
 */
class SbtRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez) {
  def createTemplateConfiguration(project: Project): RunConfiguration = new SbtRunConfiguration(project, this, "")
}