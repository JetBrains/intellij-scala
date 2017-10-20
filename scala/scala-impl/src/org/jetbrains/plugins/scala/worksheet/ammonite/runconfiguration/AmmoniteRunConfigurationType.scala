package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons

/**
  * User: Dmitry.Naydanov
  * Date: 13.09.17.
  */
class AmmoniteRunConfigurationType extends ConfigurationType with DumbAware {
  private val factory = new AmmoniteRunConfigurationFactory(this)
  
  override def getId: String = "ScalaAmmoniteRunConfigurationType"

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](factory)

  override def getIcon: Icon = Icons.SCALA_CONSOLE

  override def getDisplayName: String = "Run Ammonite"

  override def getConfigurationTypeDescription: String = "Run Ammonite script"
}
