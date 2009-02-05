package org.jetbrains.plugins.scala.script

import com.intellij.execution.configurations.{ConfigurationType, ConfigurationFactory}
import icons.Icons
import javax.swing.Icon
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptConfigurationType extends ConfigurationType {
  val confFactory = new ScalaScriptRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCRIPT_FILE_LOGO

  def getDisplayName: String = "Scala script"

  def getConfigurationTypeDescription: String = "Scala script run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaScriptRunConfiguration"
}