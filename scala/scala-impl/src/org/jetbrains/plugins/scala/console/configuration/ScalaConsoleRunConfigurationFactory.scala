package org.jetbrains.plugins.scala.console.configuration

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project._

class ScalaConsoleRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez) {
  override def createTemplateConfiguration(project: Project): RunConfiguration = {
    new ScalaConsoleRunConfiguration(project, this, "")
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = super.createConfiguration(name, template).asInstanceOf[ScalaConsoleRunConfiguration]
    template.getProject.anyScalaModule.foreach(configuration.setModule)
    configuration
  }
}