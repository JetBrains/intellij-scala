package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.{Location, RunnerAndConfigurationSettings}
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */

trait AbstractTestConfigurationProducer {
  def suitePaths: List[String]

  def createConfigurationByElement(location: Location[_ <: PsiElement],
                                   context: ConfigurationContext): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    if (context.getModule == null) return null
    val allClassesAreNull = {
      val scope: GlobalSearchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(context.getModule, true)
      val manager = ScalaPsiManager.instance(context.getProject)
      suitePaths.forall(path => manager.getCachedClass(scope, path).orNull == null)
    }
    if (allClassesAreNull) null
    else createConfigurationByLocation(location)//.asInstanceOf[RunnerAndConfigurationSettingsImpl]
  }

  def findExistingByElement(location: Location[_ <: PsiElement],
                            existingConfigurations: Array[RunnerAndConfigurationSettings],
                            context: ConfigurationContext): RunnerAndConfigurationSettings = {
    existingConfigurations.find(c => isConfigurationByLocation(c.getConfiguration, location)).orNull
  }

  def createConfigurationByLocation(location: Location[_ <: PsiElement]): Option[(PsiElement, RunnerAndConfigurationSettings)]

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean
}
