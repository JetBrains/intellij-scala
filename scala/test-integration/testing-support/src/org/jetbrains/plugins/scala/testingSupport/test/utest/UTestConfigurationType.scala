package org.jetbrains.plugins.scala
package testingSupport
package test.utest

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, ConfigurationTypeUtil}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing.Icon

class UTestConfigurationType extends ConfigurationType with DumbAware {
  val confFactory = new UTestRunConfigurationFactory(this)

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  override def getDisplayName: String = TestingSupportBundle.message("utest.config.display.name")

  override def getConfigurationTypeDescription: String = TestingSupportBundle.message("utest.config.description")

  override def getId: String = "uTestRunConfiguration" //if you want to change id, change it in Android plugin too

  override def getIcon: Icon = Icons.SCALA_TEST
}

object UTestConfigurationType {

  @deprecated("use `apply` instead", "2020.3")
  def instance: UTestConfigurationType = apply()

  def apply(): UTestConfigurationType =
    ConfigurationTypeUtil.findConfigurationType(classOf[UTestConfigurationType])
}
