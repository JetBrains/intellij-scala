package org.jetbrains.plugins.scala
package console


import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project._

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaConsoleRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez) {
  def createTemplateConfiguration(project: Project): RunConfiguration = {
    new ScalaConsoleRunConfiguration(project, this, "")
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = super.createConfiguration(name, template).asInstanceOf[ScalaConsoleRunConfiguration]
    template.getProject.anyScalaModule.foreach(configuration.setModule(_))
    configuration
  }
}