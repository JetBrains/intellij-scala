package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import javax.swing.Icon

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.icons.Icons

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

class Specs2ConfigurationType extends ConfigurationType with DumbAware {

  val confFactory = new Specs2RunConfigurationFactory(this)

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getDisplayName: String = "Specs2"

  def getConfigurationTypeDescription: String = "Specs2 testing framework run configuration"

  def getId: String = "Specs2RunConfiguration" //if you want to change id, change it in Android plugin too

  def getIcon: Icon = Icons.SCALA_TEST

}
