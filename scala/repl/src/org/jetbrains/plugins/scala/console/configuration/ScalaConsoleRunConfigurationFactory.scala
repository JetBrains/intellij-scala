package org.jetbrains.plugins.scala.console.configuration

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, RunConfiguration}
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.project.*

class ScalaConsoleRunConfigurationFactory(val typez: ConfigurationType) extends ConfigurationFactory(typez) {

  // (!!! DO NOT CHANGE IT TO "Scala REPL")
  // Preserve old factory id due to we changed display name to "Scala REPL"
  // Default implementation of `getId` delegates to `getName` which delegates to `myType.getDisplayName`
  @NonNls
  override def getId: String = "Scala Console"

  override def createTemplateConfiguration(project: Project): RunConfiguration = {
    new ScalaConsoleRunConfiguration(project, this, "")
  }

  override def createConfiguration(name: String, template: RunConfiguration): RunConfiguration = {
    val configuration = super.createConfiguration(name, template).asInstanceOf[ScalaConsoleRunConfiguration]
    template.getProject.anyScalaModule.foreach(configuration.setModule)
    configuration
  }
}