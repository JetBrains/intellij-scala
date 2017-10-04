package org.jetbrains.plugins.scala
package console


import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import org.jetbrains.plugins.scala.icons.Icons

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaConsoleConfigurationType extends ConfigurationType {
  val confFactory = new ScalaConsoleRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCALA_CONSOLE

  def getDisplayName: String = "Scala Console"

  def getConfigurationTypeDescription: String = "Scala console run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaScriptConsoleRunConfiguration"
}