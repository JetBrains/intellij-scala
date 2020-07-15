package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import javax.swing.Icon
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle

class AmmoniteRunConfigurationType extends ConfigurationType with DumbAware {
  private val factory = new AmmoniteRunConfigurationFactory(this)
  
  override def getId: String = "ScalaAmmoniteRunConfigurationType"

  override def getDisplayName: String = WorksheetBundle.message("ammonite.config.display.name")

  override def getConfigurationTypeDescription: String = WorksheetBundle.message("ammonite.config.run.ammonite.script")

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](factory)

  override def getIcon: Icon = Icons.SCALA_CONSOLE
}
