package org.jetbrains.plugins.scala.testingSupport.scalaTest

import _root_.java.lang.String
import _root_.javax.swing.Icon
import com.intellij.execution.configurations.{ConfigurationType, ConfigurationFactory}
import com.intellij.execution.LocatableConfigurationType
import com.intellij.openapi.util.IconLoader
import script.ScalaScriptRunConfigurationFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestConfigurationType extends ConfigurationType {//todo: locatable
  val confFactory = new ScalaTestRunConfigurationFactory(this)

  def getIcon: Icon = IconLoader.getIcon("/runConfigurations/junit.png")

  def getDisplayName: String = "ScalaTest"

  def getConfigurationTypeDescription: String = "ScalaTest testing framework run configuration"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaTestRunConfiguration"
}