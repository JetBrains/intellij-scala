package org.jetbrains.plugins.scala
package testingSupport
package specs2

import _root_.java.lang.String
import _root_.javax.swing.Icon
import com.intellij.execution._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import configurations.{RunConfiguration, ConfigurationFactory}
import icons.Icons
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import lang.psi.ScalaPsiUtil
import specs.SpecsRunConfiguration
import lang.psi.api.base.ScLiteral

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */
class Specs2ConfigurationType extends LocatableConfigurationType {
  val confFactory = new Specs2RunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCALA_TEST

  def getDisplayName: String = "Specs2"

  def getConfigurationTypeDescription: String = "Specs2 testing framework run configuration"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "Specs2RunConfiguration"


  def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val element = location.getPsiElement
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return null
      val displayName = ScalaBundle.message("test.in.scope.specs2.presentable.text", pack.getQualifiedName)
      val settings = RunManager.getInstance(location.getProject).createRunConfiguration(displayName, confFactory)
      settings.getConfiguration.asInstanceOf[Specs2RunConfiguration].setTestPackagePath(pack.getQualifiedName)
      settings.getConfiguration.asInstanceOf[Specs2RunConfiguration].setGeneratedName(displayName)
      return settings
    }
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    val parentLiteral: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    if (parent == null) return null
    val facade = JavaPsiFacade.getInstance(element.getProject)
    val suiteClazz: PsiClass = facade.findClass("org.specs2.specification.SpecificationStructure", element.getResolveScope)
    if (suiteClazz == null) return null
    if (!ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)) return null
    val settings = RunManager.getInstance(location.getProject).createRunConfiguration(parent.getName, confFactory)
    val runConfiguration = settings.getConfiguration.asInstanceOf[Specs2RunConfiguration]
    val testClassPath = parent.getQualifiedName
    runConfiguration.setTestClassPath(testClassPath)

    // If the selected element is a non-empty string literal, we assume that this
    // is the name of an example to be filtered.
    Option(parentLiteral) match {
      case Some(x) if x.isString =>
        x.getValue match {
          case exampleName: String if exampleName.nonEmpty =>
            val options = runConfiguration.getJavaOptions
            val exampleFilterProperty = "-Dspecs2.ex=\"" + exampleName + "\""
            runConfiguration.setJavaOptions(Seq(options,  exampleFilterProperty).mkString(" "))
            val name = testClassPath + "::" + exampleName
            runConfiguration.setGeneratedName(name)
            runConfiguration.setName(name)
          case _ =>
        }
      case _ =>
    }
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
        case configuration: SpecsRunConfiguration => {
          return configuration.getTestPackagePath == pack.getQualifiedName
        }
        case _ => return false
      }
    }
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition])
    if (parent == null) return false
    val facade = JavaPsiFacade.getInstance(element.getProject)
    val suiteClazz: PsiClass = facade.findClass("org.specs2.specification.SpecificationStructure", element.getResolveScope)
    if (suiteClazz == null) return false
    if (!ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)) return false
    configuration match {
      case configuration: SpecsRunConfiguration => return parent.getQualifiedName == configuration.getTestClassPath
      case _ => return false
    }
  }
}