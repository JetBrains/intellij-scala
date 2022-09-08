package org.jetbrains.plugins.scala
package testingSupport
package test.scalatest

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, ConfigurationTypeUtil}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing.Icon

class ScalaTestConfigurationType extends ConfigurationType with DumbAware {

  val confFactory = new ScalaTestRunConfigurationFactory(this)

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  override def getDisplayName: String = TestingSupportBundle.message("scalatest.config.display.name")

  override def getConfigurationTypeDescription: String = TestingSupportBundle.message("scalatest.config.description")

  override def getId: String = "ScalaTestRunConfiguration" //if you want to change id, change it in Android plugin too

  override def getIcon: Icon = Icons.SCALA_TEST
}

object ScalaTestConfigurationType {

  @deprecated("use `apply` instead", "2020.3")
  def instance: ScalaTestConfigurationType = apply()

  def apply(): ScalaTestConfigurationType =
    ConfigurationTypeUtil.findConfigurationType(classOf[ScalaTestConfigurationType])
}
