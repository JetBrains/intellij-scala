package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution._
import com.intellij.psi.util.PsiTreeUtil
import configurations.RunConfiguration
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import testingSupport.RuntimeConfigurationProducerAdapter
import lang.psi.impl.ScalaPsiManager
import lang.psi.ScalaPsiUtil
import lang.psi.api.base.ScLiteral
import testingSupport.test.TestRunConfigurationForm.TestKind
import com.intellij.execution.actions.ConfigurationContext
import org.jetbrains.plugins.scala.testingSupport.test.{TestConfigurationProducer, TestConfigurationUtil, AbstractTestConfigurationProducer}

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.05.2009
 */

class Specs2ConfigurationProducer extends {
  val confType = new Specs2ConfigurationType
  val confFactory = confType.confFactory
} with TestConfigurationProducer(confType) with AbstractTestConfigurationProducer {

  override def suitePath = "org.specs2.specification.SpecificationStructure"

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

    // If the selected element is a non-empty string literal, we assume that this
    // is the name of an example to be filtered.
    if (testName != null) {
      val options = runConfiguration.getJavaOptions
      val exampleFilterProperty = "-Dspecs2.ex=\"" + testName + "\""
      runConfiguration.setJavaOptions(Seq(options,  exampleFilterProperty).mkString(" "))
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
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition])
    if (parent == null) return false
    val suiteClazz: PsiClass = ScalaPsiManager.instance(parent.getProject).
      getCachedClass("org.specs2.specification.SpecificationStructure",
      element.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
    if (suiteClazz == null) return false
    if (!ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)) return false

    val parentLiteral: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    val testClassPath = parent.qualifiedName
    val testClassName = Option(parentLiteral) match {
      case Some(x) if x.isString =>
        x.getValue match {
          case exampleName: String if exampleName.nonEmpty =>
            exampleName
          case _ => null
        }
      case _ => null
    }

    configuration match {
      case configuration: Specs2RunConfiguration if configuration.getTestKind() == TestKind.CLASS &&
        testClassName == null =>
        testClassPath == configuration.getTestClassPath
      case configuration: Specs2RunConfiguration if configuration.getTestKind() == TestKind.TEST_NAME =>
        testClassPath == configuration.getTestClassPath && testClassName != null &&
          testClassName == configuration.getTestName()
      case _ => false
    }
  }

  def getLocationClassAndTest(location: Location[_ <: PsiElement]): (String, String) = {
    val element = location.getPsiElement
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    val parentLiteral: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    if (parent == null) return (null, null)
    val suiteClazz: PsiClass = ScalaPsiManager.instance(parent.getProject).
      getCachedClass("org.specs2.specification.SpecificationStructure",
      element.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
    if (suiteClazz == null) return (null, null)
    if (!ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)) return null
    val testClassPath = parent.qualifiedName

    // If the selected element is a non-empty string literal, we assume that this
    // is the name of an example to be filtered.
    val testName = Option(parentLiteral) match {
      case Some(x) if x.isString =>
        x.getValue match {
          case exampleName: String if exampleName.nonEmpty =>
            exampleName
          case _ =>
            null
        }
      case _ => null
    }
    (testClassPath, testName)
  }
}