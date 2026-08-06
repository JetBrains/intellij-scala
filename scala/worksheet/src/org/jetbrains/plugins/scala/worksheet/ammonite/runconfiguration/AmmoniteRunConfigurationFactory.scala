package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

class AmmoniteRunConfigurationFactory(tpe: ConfigurationType) extends ConfigurationFactory(tpe) {
  override def getId: String = "Ammonite"
  override def createTemplateConfiguration(project: Project): RunConfiguration = 
    new AmmoniteRunConfiguration(project, this)
}
