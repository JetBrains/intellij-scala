package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.{Location, RunnerAndConfigurationSettings}
import com.intellij.mock.MockModule
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */

trait AbstractTestConfigurationProducer {

  protected def suitePaths: List[String]

  def createConfigurationByLocation(location: Location[_ <: PsiElement]): Option[(PsiElement, RunnerAndConfigurationSettings)]

  protected def createConfigurationByElement(location: Location[_ <: PsiElement],
                                             context: ConfigurationContext): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    context.getModule match {
      case module: Module if hasTestSuitesInModuleDependencies(module) =>
        createConfigurationByLocation(location)//.asInstanceOf[RunnerAndConfigurationSettingsImpl]
      case _ =>
        null
    }
  }

  private def hasTestSuitesInModuleDependencies(module: Module): Boolean = {
    val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
    val psiManager = ScalaPsiManager.instance(module.getProject)
    suitePaths.exists(psiManager.getCachedClass(scope, _).isDefined)
  }

  protected def findExistingByElement(location: Location[_ <: PsiElement],
                                      existingConfigurations: Array[RunnerAndConfigurationSettings],
                                      context: ConfigurationContext): RunnerAndConfigurationSettings = {
    existingConfigurations.find(c => isConfigurationByLocation(c.getConfiguration, location)).orNull
  }

  protected def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean
}
