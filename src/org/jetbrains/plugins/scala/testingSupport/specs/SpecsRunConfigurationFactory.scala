package org.jetbrains.plugins.scala
package testingSupport
package specs



import com.intellij.execution.configurations.{RunConfiguration, ConfigurationFactory, ConfigurationType}
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleManager
import scalaTest.ScalaTestRunConfiguration
import com.intellij.openapi.project.Project
import config.ScalaFacet

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

class SpecsRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez)  {
  def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new SpecsRunConfiguration(project, this, "")
    return configuration
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = (super.createConfiguration(name, template)).asInstanceOf[SpecsRunConfiguration]
    ScalaFacet.findModulesIn(template.getProject).headOption.foreach {
      configuration.setModule _
    }
    configuration
  }
}