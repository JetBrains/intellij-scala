package org.jetbrains.plugins.scala
package testingSupport
package scalaTest

import _root_.java.lang.String
import _root_.javax.swing.Icon
import com.intellij.execution.configurations.{JavaRunConfigurationModule, RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.{RunManager, Location, RunnerAndConfigurationSettings, LocatableConfigurationType}
import com.intellij.openapi.util.IconLoader
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import icons.Icons
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import script.ScalaScriptRunConfigurationFactory
import lang.psi.ScalaPsiUtil
import com.intellij.openapi.module.{ModuleManager, Module}
import com.intellij.facet.FacetManager

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaTestConfigurationType extends LocatableConfigurationType {
  val confFactory = new ScalaTestRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCALA_TEST

  def getDisplayName: String = "ScalaTest"

  def getConfigurationTypeDescription: String = "ScalaTest testing framework run configuration"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "ScalaTestRunConfiguration"


  def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val element = location.getPsiElement
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return null
      val displayName = ScalaBundle.message("test.in.scope.scalatest.presentable.text", pack.getQualifiedName)
      val settings = RunManager.getInstance(location.getProject).createRunConfiguration(displayName, confFactory)
      settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration].setTestPackagePath(pack.getQualifiedName)
      settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration].setGeneratedName(displayName)
      return settings
    }
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (parent == null) return null
    val facade = JavaPsiFacade.getInstance(element.getProject)
    val suiteClazz = facade.findClass("org.scalatest.Suite", GlobalSearchScope.allScope(element.getProject))
    if (suiteClazz == null) return null
    if (!ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)) return null
    val settings = RunManager.getInstance(location.getProject).createRunConfiguration(parent.getName, confFactory)
    val testClassPath = parent.getQualifiedName
    val runConfiguration = settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration]
    runConfiguration.setTestClassPath(testClassPath)
    try {
      val module = ScalaPsiUtil.getModule(element)
      if (module != null) {
        runConfiguration.setModule(module)
      }
    }
    catch {
      case e =>
    }
    return settings
  }

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return false
      configuration match {
        case configuration: ScalaTestRunConfiguration => {
          return configuration.getTestPackagePath == pack.getQualifiedName
        }
        case _ => return false
      }
    }
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition])
    if (parent == null) return false
    val facade = JavaPsiFacade.getInstance(element.getProject)
    val suiteClazz = facade.findClass("org.scalatest.Suite", GlobalSearchScope.allScope(element.getProject))
    if (suiteClazz == null) return false
    if (!ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)) return false
    configuration match {
      case configuration: ScalaTestRunConfiguration => return parent.getQualifiedName == configuration.getTestClassPath
      case _ => return false
    }             
  }
}