package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.psi._
import com.intellij.execution.{RunnerAndConfigurationSettings, Location, JavaRunConfigurationExtensionManager, RunManager}
import com.intellij.execution.configurations.RunConfiguration
import lang.psi.api.toplevel.typedef.ScClass
import lang.psi.api.toplevel.ScModifierListOwner

/**
 * @author Ksenia.Sautina
 * @since 5/22/12
 */

object TestConfigurationUtil {

  def packageSettings(element: PsiElement, location: Location[_ <: PsiElement],
                      confFactory: AbstractTestRunConfigurationFactory,
                      message: String): RunnerAndConfigurationSettings = {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return null
      val displayName = ScalaBundle.message(message, pack.getQualifiedName)
      val settings = RunManager.getInstance(location.getProject).createRunConfiguration(displayName, confFactory)
      val configuration = settings.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
      configuration.setTestPackagePath(pack.getQualifiedName)
      configuration.setTestKind(TestRunConfigurationForm.TestKind.ALL_IN_PACKAGE)
      configuration.setGeneratedName(displayName)
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
      case configuration: AbstractTestRunConfiguration => {
        configuration.getTestKind() == TestRunConfigurationForm.TestKind.ALL_IN_PACKAGE &&
                configuration.getTestPackagePath == pack.getQualifiedName
      }
      case _ => false
    }
  }

  def lackNoArgConstructor(clazz: PsiClass): Boolean = {
    clazz match {
      case c: ScClass =>
        val constructors = c.secondaryConstructors.filter(_.isConstructor).toList ::: c.constructor.toList
        for (con <- constructors) {
          if (con.isConstructor && con.parameterList.getParametersCount == 0) {
            if (con.isInstanceOf[ScModifierListOwner]) {
              if (con.asInstanceOf[ScModifierListOwner].hasModifierProperty("public")) return false
            }
          }
        }

      case _ =>
        for (constructor <- clazz.getConstructors) {
          if (constructor.isConstructor && constructor.getParameterList.getParametersCount == 0) {
            if (constructor.isInstanceOf[ScModifierListOwner]) {
              if (constructor.asInstanceOf[ScModifierListOwner].hasModifierProperty("public")) return false
            }
          }
        }
    }
    true
  }

  def isInvalidSuite(clazz: PsiClass): Boolean = {
    clazz.getModifierList.hasModifierProperty("abstract") || lackNoArgConstructor(clazz)
  }

}
