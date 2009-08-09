package org.jetbrains.plugins.scala
package script


import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import javax.swing.Icon
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez) {
  def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new ScalaScriptRunConfiguration(project, this, "")
    initDefault(configuration)
    return configuration
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = (super.createConfiguration(name, template)).asInstanceOf[ScalaScriptRunConfiguration]
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

  private def initDefault(configuration: ScalaScriptRunConfiguration): Unit = {
  }
}