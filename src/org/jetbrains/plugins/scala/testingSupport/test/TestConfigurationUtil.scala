package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.{JavaRunConfigurationExtensionManager, Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.psi._

/**
 * @author Ksenia.Sautina
 * @since 5/22/12
 */

object TestConfigurationUtil {

  def packageSettings(element: PsiElement, location: Location[_ <: PsiElement],
                      confFactory: AbstractTestRunConfigurationFactory,
                      displayName: String): RunnerAndConfigurationSettings = {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return null
      val settings = RunManager.getInstance(location.getProject).createRunConfiguration(displayName, confFactory)
      val configuration = settings.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
      configuration.setTestPackagePath(pack.getQualifiedName)
      configuration.setTestKind(TestRunConfigurationForm.TestKind.ALL_IN_PACKAGE)
      configuration.setGeneratedName(displayName)
      configuration.setModule(location.getModule)
      JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
      settings
  }

  def isPackageConfiguration(element: PsiElement, configuration: RunConfiguration): Boolean = {
    val pack: PsiPackage = element match {
      case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
      case pack: PsiPackage => pack
    }
    if (pack == null) return false
    configuration match {
      case configuration: AbstractTestRunConfiguration =>
        configuration.getTestKind == TestRunConfigurationForm.TestKind.ALL_IN_PACKAGE &&
                configuration.getTestPackagePath == pack.getQualifiedName
      case _ => false
    }
  }
}
