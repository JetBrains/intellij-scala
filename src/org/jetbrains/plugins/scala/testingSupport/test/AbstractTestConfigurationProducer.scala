package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.{Location, RunnerAndConfigurationSettings}
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */

trait AbstractTestConfigurationProducer {
  private var myPsiElement: PsiElement = null
  def getSourceElement: PsiElement = myPsiElement

  def suitePaths: List[String]

  def createConfigurationByElement(location: Location[_ <: PsiElement],
                                             context: ConfigurationContext): RunnerAndConfigurationSettingsImpl = {
    if (context.getModule == null) return null
    val scope: GlobalSearchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(context.getModule, true)
    if (suitePaths.forall(
      suitePath => ScalaPsiManager.instance(context.getProject).getCachedClass(scope, suitePath) == null)) return null
    myPsiElement = location.getPsiElement
    createConfigurationByLocation(location).asInstanceOf[RunnerAndConfigurationSettingsImpl]
  }

  def findExistingByElement(location: Location[_ <: PsiElement],
                                      existingConfigurations: Array[RunnerAndConfigurationSettings],
                                      context: ConfigurationContext): RunnerAndConfigurationSettings = {
    existingConfigurations.find(c => isConfigurationByLocation(c.getConfiguration, location)).orNull
  }

  def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean


  protected def escapeAndConcatTestNames(testNames: List[String]) = {
    val res = testNames.map(escapeTestName)
    if (res.size > 0) res.tail.fold(res.head)(_+"\n"+_) else ""
  }

  protected def escapeTestName(testName: String) = testName.replace("\\", "\\\\").replace("\n", "\\n")
}
