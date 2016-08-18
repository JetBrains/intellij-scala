package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution._
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
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

  override def createConfigurationByLocation(location: Location[_ <: PsiElement]): Option[(PsiElement, RunnerAndConfigurationSettings)] = {
    val element = location.getPsiElement
    if (element == null) return None

    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val name = element match {
        case p: PsiPackage => p.getName
        case d: PsiDirectory => d.getName
      }
      return Some((element, TestConfigurationUtil.packageSettings(element, location, confFactory, ScalaBundle.message("test.in.scope.specs2.presentable.text", name))))
    }

    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)

    if (parent == null) return None

    val settings = RunManager.getInstance(location.getProject).createRunConfiguration(parent.name, confFactory)
    val runConfiguration = settings.getConfiguration.asInstanceOf[Specs2RunConfiguration]
    val (testClass, testName) = getLocationClassAndTest(location)
    if (testClass == null) return None
    val testClassPath = testClass.qualifiedName
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
      case _: Exception =>
    }
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(runConfiguration, location)
    Some((testClass, settings))
  }

  override def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    if (element == null) return false
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      if (!configuration.isInstanceOf[Specs2RunConfiguration]) return false
      return TestConfigurationUtil.isPackageConfiguration(element, configuration)
    }

    appropriateParent(element) match {
      case None => return false
      case _ =>
    }

    val (testClass, testName) = getLocationClassAndTest(location)
    if (testClass == null) return false
    val testClassPath = testClass.qualifiedName

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

  private def extractStaticTestName(testDefExpr: ScInfixExpr): Option[String] = {
    testDefExpr.getChildren.filter(_.isInstanceOf[ScExpression]).map(_.asInstanceOf[ScExpression]).headOption.
      flatMap(TestConfigurationUtil.getStaticTestName(_))
  }

  def getLocationClassAndTest(location: Location[_ <: PsiElement]): (ScTypeDefinition, String) = {
    val element = location.getPsiElement
    appropriateParent(element).map { testClassDef =>
      ScalaPsiUtil.getParentWithProperty(element, strict = false, e => TestNodeProvider.isSpecs2TestExpr(e)) match {
        case Some(infixExpr: ScInfixExpr) => (testClassDef, extractStaticTestName(infixExpr).orNull)
        case _ => (testClassDef, null)
      }
    }.getOrElse(null, null)
  }

  private def appropriateParent(element: PsiElement): Option[ScTypeDefinition] =
    Option(PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)).filter { parent =>
      val psiManager = ScalaPsiManager.instance(parent.getProject)
      suitePaths.flatMap { suite =>
        psiManager.getCachedClass(suite, element.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
      }.headOption
        .exists(ScalaPsiUtil.cachedDeepIsInheritor(parent, _))
    }
}
