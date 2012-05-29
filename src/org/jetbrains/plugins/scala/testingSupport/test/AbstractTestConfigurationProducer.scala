package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.psi.search.GlobalSearchScope
import lang.psi.impl.ScalaPsiManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.psi.PsiElement
import com.intellij.execution.{RunnerAndConfigurationSettings, Location}

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */

trait AbstractTestConfigurationProducer {
  private var myPsiElement: PsiElement = null
  def getSourceElement: PsiElement = myPsiElement

  def suitePath: String

  def createConfigurationByElement(location: Location[_ <: PsiElement],
                                             context: ConfigurationContext): RunnerAndConfigurationSettingsImpl = {
    if (context.getModule == null) return null
    val scope: GlobalSearchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(context.getModule, true)
    if (ScalaPsiManager.instance(context.getProject).getCachedClass(scope, suitePath) == null) return null
    myPsiElement = location.getPsiElement
    createConfigurationByLocation(location).asInstanceOf[RunnerAndConfigurationSettingsImpl]
  }

  def findExistingByElement(location: Location[_ <: PsiElement],
                                      existingConfigurations: Array[RunnerAndConfigurationSettings],
                                      context: ConfigurationContext): RunnerAndConfigurationSettings = {
    existingConfigurations.find(c => isConfigurationByLocation(c.getConfiguration, location)).getOrElse(null)
  }

  def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean

}
