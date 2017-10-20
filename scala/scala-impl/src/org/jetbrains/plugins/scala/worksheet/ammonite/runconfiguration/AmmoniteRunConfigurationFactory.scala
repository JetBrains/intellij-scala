package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project

/**
  * User: Dmitry.Naydanov
  * Date: 13.09.17.
  */
class AmmoniteRunConfigurationFactory(tpe: ConfigurationType) extends ConfigurationFactory(tpe) {
  override def createTemplateConfiguration(project: Project): RunConfiguration = 
    new AmmoniteRunConfiguration(project, this)
}
