package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestConfigurationType extends ConfigurationType with DumbAware {

  val confFactory = new ScalaTestRunConfigurationFactory(this)

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getDisplayName: String = "ScalaTest"

  def getConfigurationTypeDescription: String = "ScalaTest testing framework run configuration"

  def getId: String = "ScalaTestRunConfiguration" //if you want to change id, change it in Android plugin too

  def getIcon: Icon = Icons.SCALA_TEST

}
