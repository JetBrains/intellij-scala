package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution._
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationProducer, TestConfigurationUtil}

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.05.2009
 */

class Specs2ConfigurationProducer extends {
  val confType = new Specs2ConfigurationType
  val confFactory = confType.confFactory
} with TestConfigurationProducer(confType) with AbstractTestConfigurationProducer {

  override def suitePaths = List("org.specs2.specification.SpecificationStructure",
    "org.specs2.specification.core.SpecificationStructure")

  override def findExistingByElement(location: Location[_ <: PsiElement],
                                     existingConfigurations: Array[RunnerAndConfigurationSettings],
                                     context: ConfigurationContext): RunnerAndConfigurationSettings = {
    super.findExistingByElement(location, existingConfigurations, context)
  }

  override def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val element = location.getPsiElement
    if (element == null) return null

    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val name = element match {
        case p: PsiPackage => p.getName
        case d: PsiDirectory => d.getName
      }
      return TestConfigurationUtil.packageSettings(element, location, confFactory, ScalaBundle.message("test.in.scope.specs2.presentable.text", name))
    }

    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)

    if (parent == null) return null

    val settings = RunManager.getInstance(location.getProject).createRunConfiguration(parent.name, confFactory)
    val runConfiguration = settings.getConfiguration.asInstanceOf[Specs2RunConfiguration]
    val (testClassPath, testName) = getLocationClassAndTest(location)
    if (testClassPath == null) return null
    runConfiguration.setTestClassPath(testClassPath)
    runConfiguration.setTestKind(TestKind.CLASS)
    runConfiguration.initWorkingDir()

    // If the selected element is a non-empty string literal, we assume that this
    // is the name of an example to be filtered.
    if (testName != null) {
      val options = runConfiguration.getJavaOptions
      runConfiguration.setJavaOptions(options)
      val name = testClassPath + "::" + testName
      runConfiguration.setGeneratedName(name)
      runConfiguration.setName(name)
      runConfiguration.setTestName(testName)
      runConfiguration.setTestKind(TestKind.TEST_NAME)
    }
    try {
      val module = ScalaPsiUtil.getModule(element)
      if (module != null) {
        runConfiguration.setModule(module)
      }
    }
    catch {
      case e: Exception =>
    }
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(runConfiguration, location)
    settings
  }

  override def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    if (element == null) return false
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      if (!configuration.isInstanceOf[Specs2RunConfiguration]) return false
      return TestConfigurationUtil.isPackageConfiguration(element, configuration)
    }
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (parent == null) return false
    val suiteClasses = suitePaths.map(suite =>
       ScalaPsiManager.instance(parent.getProject).getCachedClass(suite, element.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)).filter(_ != null)
    if (suiteClasses.isEmpty) return false
    val suiteClazz = suiteClasses.head

    if (!ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)) return false

    val parentLiteral: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    val testClassPath = parent.qualifiedName
    val testName = Option(parentLiteral) match {
      case Some(x) if x.isString =>
        x.getValue match {
          case exampleName: String if exampleName.nonEmpty =>
            exampleName
          case _ => null
        }
      case _ => null
    }

    configuration match {
      case configuration: Specs2RunConfiguration if configuration.getTestKind == TestKind.CLASS &&
        testName == null =>
        testClassPath == configuration.getTestClassPath
      case configuration: Specs2RunConfiguration if configuration.getTestKind == TestKind.TEST_NAME =>
        testClassPath == configuration.getTestClassPath && testName != null &&
          testName == configuration.getTestName
      case _ => false
    }
  }

  def getLocationClassAndTest(location: Location[_ <: PsiElement]): (String, String) = {
    val element = location.getPsiElement
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    val parentLiteral: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    if (parent == null) return (null, null)
    val psiManager = ScalaPsiManager.instance(parent.getProject)
    val suiteClasses = suitePaths.map(suite =>
      psiManager.getCachedClass(suite, element.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)).filter(_ != null)
    if (suiteClasses.isEmpty) return (null, null)
    val suiteClazz = suiteClasses.head
    if (!ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)) return (null, null)
    val testClassPath = parent.qualifiedName

    // If the selected element is a non-empty string literal, we assume that this
    // is the name of an example to be filtered.
    val testName = Option(parentLiteral) match {
      case Some(x) if x.isString =>
        x.getValue match {
          case exampleName: String if exampleName.nonEmpty =>
            escapeTestName(exampleName)
          case _ =>
            null
        }
      case _ => null
    }
    (testClassPath, testName)
  }
}