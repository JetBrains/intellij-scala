package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType, ConfigurationTypeUtil}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing.Icon

class Specs2ConfigurationType extends ConfigurationType with DumbAware {

  val confFactory = new Specs2RunConfigurationFactory(this)

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  override def getDisplayName: String = ScalaBundle.message("specs2.config.display.name")

  override def getConfigurationTypeDescription: String = ScalaBundle.message("specs2.config.description")

  override def getId: String = "Specs2RunConfiguration" //if you want to change id, change it in Android plugin too

  override def getIcon: Icon = Icons.SCALA_TEST
}

object Specs2ConfigurationType {

  @deprecated("use `apply` instead", "2020.3")
  def instance: Specs2ConfigurationType = apply()

  def apply(): Specs2ConfigurationType =
    ConfigurationTypeUtil.findConfigurationType(classOf[Specs2ConfigurationType])
}