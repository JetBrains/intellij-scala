package org.jetbrains.plugins.scala
package script

import com.intellij.execution.configurations.{ConfigurationType, RunConfiguration, ConfigurationFactory}
import com.intellij.execution.{RunManager, Location, RunnerAndConfigurationSettings}
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiElement, PsiFile}
import config.ScalaFacet
import icons.Icons
import javax.swing.Icon
import java.lang.String
import lang.psi.api.ScalaFile
import extensions.toPsiNamedElementExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptConfigurationType extends ConfigurationType {
  val confFactory = new ScalaScriptRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCRIPT_FILE_LOGO

  def getDisplayName: String = "Scala Script"

  def getConfigurationTypeDescription: String = "Scala script run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaScriptRunConfiguration"
}