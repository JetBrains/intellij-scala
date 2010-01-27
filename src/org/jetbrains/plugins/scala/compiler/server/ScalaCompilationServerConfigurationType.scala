package org.jetbrains.plugins.scala.compiler.server

import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import javax.swing.Icon

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.2010
 */

//todo: remove this after fsc works ok
class ScalaCompilationServerConfigurationType extends ConfigurationType {
  val confFactory = new ScalaCompilationServerRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCALA_CONSOLE

  def getDisplayName: String = "Scala Compilation Server"

  def getConfigurationTypeDescription: String = "Scala compilation server run configuration"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaCompilationServerRunConfiguration"
}