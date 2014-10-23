package org.jetbrains.plugins.scala
package script


import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project._

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
    template.getProject.anyScalaModule.foreach(configuration.setModule(_))
    configuration
  }

  private def initDefault(configuration: ScalaScriptRunConfiguration): Unit = {
  }
}