package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.execution.configurations.{ConfigurationType, ConfigurationFactory}

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

class Specs2ConfigurationType extends ConfigurationType {

  val confFactory = new Specs2RunConfigurationFactory(this)

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getDisplayName: String = "Specs2"

  def getConfigurationTypeDescription: String = "Specs2 testing framework run configuration"

  def getId: String = "Specs2RunConfiguration" //if you want to change id, change it in Android plugin too

  def getIcon: Icon = Icons.SCALA_TEST

}
