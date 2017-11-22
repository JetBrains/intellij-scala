package org.jetbrains.plugins.scala
package testingSupport.test.utest

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons

class UTestConfigurationType extends ConfigurationType with DumbAware {
  val confFactory = new UTestRunConfigurationFactory(this)

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getDisplayName: String = "utest"

  def getConfigurationTypeDescription: String = "utest testing framework run configuration"

  def getId: String = "uTestRunConfiguration" //if you want to change id, change it in Android plugin too

  def getIcon: Icon = Icons.SCALA_TEST

}
