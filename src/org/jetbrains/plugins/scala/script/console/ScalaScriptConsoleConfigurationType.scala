package org.jetbrains.plugins.scala.script.console


import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.psi.{PsiElement, PsiFile}
import config.ScalaFacet
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.execution.{RunManager, RunnerAndConfigurationSettings, LocatableConfigurationType}
import icons.Icons
import javax.swing.Icon
import lang.psi.api.ScalaFile


/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

class ScalaScriptConsoleConfigurationType extends ConfigurationType {
  val confFactory = new ScalaScriptConsoleRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCRIPT_FILE_LOGO

  def getDisplayName: String = "Scala Console"

  def getConfigurationTypeDescription: String = "Scala console run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaScriptConsoleRunConfiguration"
}