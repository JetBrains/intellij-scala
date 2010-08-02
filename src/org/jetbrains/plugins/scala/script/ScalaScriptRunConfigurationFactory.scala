package org.jetbrains.plugins.scala
package script


import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import javax.swing.Icon
import java.lang.String
import config.ScalaFacet

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
    ScalaFacet.findModulesIn(template.getProject).headOption.foreach {
      configuration.setModule _
    }
    configuration  }

  private def initDefault(configuration: ScalaScriptRunConfiguration): Unit = {
  }
}