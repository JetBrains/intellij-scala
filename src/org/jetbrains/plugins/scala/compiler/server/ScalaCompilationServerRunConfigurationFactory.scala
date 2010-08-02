package org.jetbrains.plugins.scala.compiler.server

import com.intellij.openapi.project.Project
import com.intellij.openapi.module.ModuleManager
import com.intellij.facet.FacetManager
import com.intellij.execution.configurations.{ConfigurationType, ConfigurationFactory, RunConfiguration}
import org.jetbrains.plugins.scala.config.ScalaFacet

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.2010
 */

class ScalaCompilationServerRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez) {
  def createTemplateConfiguration(project: Project): RunConfiguration = {
    val configuration = new ScalaCompilationServerRunConfiguration(project, this, "")
    initDefault(configuration)
    return configuration
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = (super.createConfiguration(name, template)).asInstanceOf[ScalaCompilationServerRunConfiguration]
    ScalaFacet.findModulesIn(template.getProject).headOption.foreach {
      configuration.setModule _
    }
    configuration
  }

  private def initDefault(configuration: ScalaCompilationServerRunConfiguration): Unit = {
  }
}