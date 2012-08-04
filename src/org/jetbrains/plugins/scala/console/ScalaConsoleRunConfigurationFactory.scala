package org.jetbrains.plugins.scala
package console


import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.openapi.project.Project
import config.ScalaFacet

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaConsoleRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez) {
  def createTemplateConfiguration(project: Project): RunConfiguration = {
    new ScalaConsoleRunConfiguration(project, this, "")
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = (super.createConfiguration(name, template)).asInstanceOf[ScalaConsoleRunConfiguration]
    ScalaFacet.findModulesIn(template.getProject).headOption.foreach {
      configuration.setModule _
    }
    configuration  }
}