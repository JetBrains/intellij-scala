package org.jetbrains.plugins.scala.testingSupport.specs2

import com.intellij.execution.configurations.{RunConfiguration, ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.config.ScalaFacet

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

class Specs2RunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez)  {
  def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new Specs2RunConfiguration(project, this, "")
    return configuration
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = (super.createConfiguration(name, template)).asInstanceOf[Specs2RunConfiguration]
    ScalaFacet.findModulesIn(template.getProject).headOption.foreach {
      configuration.setModule _
    }
    configuration
  }
}