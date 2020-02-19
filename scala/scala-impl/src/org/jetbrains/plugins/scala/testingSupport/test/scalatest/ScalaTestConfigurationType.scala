package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import javax.swing.Icon
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.icons.Icons

class ScalaTestConfigurationType extends ConfigurationType with DumbAware {

  val confFactory = new ScalaTestRunConfigurationFactory(this)

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  override def getDisplayName: String = ScalaBundle.message("scalatest.config.display.name")

  override def getConfigurationTypeDescription: String = ScalaBundle.message("scalatest.config.description")

  override def getId: String = "ScalaTestRunConfiguration" //if you want to change id, change it in Android plugin too

  override def getIcon: Icon = Icons.SCALA_TEST
}
