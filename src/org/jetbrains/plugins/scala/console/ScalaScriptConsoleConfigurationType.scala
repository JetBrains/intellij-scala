package org.jetbrains.plugins.scala
package console


import com.intellij.execution.configurations.{ConfigurationType, ConfigurationFactory}
import icons.Icons
import javax.swing.Icon

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaScriptConsoleConfigurationType extends ConfigurationType {
  val confFactory = new ScalaScriptConsoleRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCALA_CONSOLE

  def getDisplayName: String = "Scala Console"

  def getConfigurationTypeDescription: String = "Scala console run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaScriptConsoleRunConfiguration"
}