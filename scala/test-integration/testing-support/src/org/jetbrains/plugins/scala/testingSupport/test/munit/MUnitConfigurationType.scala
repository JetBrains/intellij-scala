package org.jetbrains.plugins.scala.testingSupport
package test.munit

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, ConfigurationTypeUtil}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing.Icon

@ApiStatus.Experimental
final class MUnitConfigurationType extends ConfigurationType {

  val confFactory = new MUnitConfigurationFactory(this)

  override def getId: String = "MUnitRunConfiguration"

  override def getDisplayName: String = TestingSupportBundle.message("munit.config.display.name")

  override def getConfigurationTypeDescription: String = TestingSupportBundle.message("munit.config.description")

  override def getHelpTopic: String = super.getHelpTopic

  override def getIcon: Icon = Icons.SCALA_TEST

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array(confFactory)
}

object MUnitConfigurationType {

  def apply(): MUnitConfigurationType =
    ConfigurationTypeUtil.findConfigurationType(classOf[MUnitConfigurationType])
}

