package org.jetbrains.plugins.scala.testingSupport.specs



import com.intellij.execution.configurations.{RunConfiguration, ConfigurationFactory, ConfigurationType}
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleManager
import scalaTest.ScalaTestRunConfiguration
import com.intellij.openapi.project.Project

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