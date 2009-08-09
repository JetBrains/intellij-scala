package org.jetbrains.plugins.scala
package testingSupport
package scalaTest


import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleManager
import script.ScalaScriptRunConfiguration
import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez)  {
  def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new ScalaTestRunConfiguration(project, this, "")
    return configuration
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = (super.createConfiguration(name, template)).asInstanceOf[ScalaTestRunConfiguration]
    val modules = ModuleManager.getInstance(template.getProject).getModules
    for (module <- modules) {
      val facetManager = FacetManager.getInstance(module)
      if (facetManager.getFacetByType(org.jetbrains.plugins.scala.config.ScalaFacet.ID) != null) {
        configuration.setModule(module)
        return configuration
      }
    }
    return configuration
  }
}