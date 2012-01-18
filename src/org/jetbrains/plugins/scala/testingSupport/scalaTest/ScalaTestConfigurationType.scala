package org.jetbrains.plugins.scala
package testingSupport
package scalaTest

import javax.swing.Icon
import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import icons.Icons
import lang.psi.ScalaPsiUtil
import com.intellij.execution._
import lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestConfigurationType extends ConfigurationType {
  val confFactory = new ScalaTestRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCALA_TEST

  def getDisplayName: String = "ScalaTest"

  def getConfigurationTypeDescription: String = "ScalaTest testing framework run configuration"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaTestRunConfiguration" //if you want to change id, change it in Android plugin too
}