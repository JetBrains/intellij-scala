package org.jetbrains.plugins.scala
package script

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptConfigurationType extends ConfigurationType with DumbAware {
  val confFactory = new ScalaScriptRunConfigurationFactory(this)
  
  def getIcon: Icon = Icons.SCRIPT_FILE_LOGO

  def getDisplayName: String = "Scala Script"

  def getConfigurationTypeDescription: String = "Scala script run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaScriptRunConfiguration"
}