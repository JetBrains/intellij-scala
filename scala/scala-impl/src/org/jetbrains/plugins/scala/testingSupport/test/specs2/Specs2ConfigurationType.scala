package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import javax.swing.Icon
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.icons.Icons

class Specs2ConfigurationType extends ConfigurationType with DumbAware {

  val confFactory = new Specs2RunConfigurationFactory(this)

  override def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  override def getDisplayName: String = ScalaBundle.message("specs2.config.display.name")

  override def getConfigurationTypeDescription: String = ScalaBundle.message("specs2.config.description")

  override def getId: String = "Specs2RunConfiguration" //if you want to change id, change it in Android plugin too

  override def getIcon: Icon = Icons.SCALA_TEST
}
