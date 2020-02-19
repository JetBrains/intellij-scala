package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import javax.swing.Icon
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.icons.Icons

class AmmoniteRunConfigurationType extends ConfigurationType with DumbAware {
  private val factory = new AmmoniteRunConfigurationFactory(this)
  
  override def getId: String = "ScalaAmmoniteRunConfigurationType"

  override def getDisplayName: String = ScalaBundle.message("ammonite.config.display.name")

  override def getConfigurationTypeDescription: String = ScalaBundle.message("ammonite.config.run.ammonite.script")

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](factory)

  override def getIcon: Icon = Icons.SCALA_CONSOLE
}
