package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution._
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationTypeUtil, RunConfiguration}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{ClassTestData, SingleTestData}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationUtil}

class Specs2ConfigurationProducer extends AbstractTestConfigurationProducer[Specs2RunConfiguration] {

  override def configurationFactory: ConfigurationFactory = {
    val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[Specs2ConfigurationType])
    configurationType.confFactory
  }

  override def suitePaths = List(
    "org.specs2.specification.SpecificationStructure",
    "org.specs2.specification.core.SpecificationStructure"
  )

  override protected def configurationNameForPackage(packageName: String): String = ScalaBundle.message("test.in.scope.specs2.presentable.text", packageName)

  //TODO: move logic from prepareRunConfiguration, like it is done in other test frameworks
  override protected def configurationName(testClass: ScTypeDefinition, testName: String): String =
    testClass.name

  override protected def prepareRunConfiguration(runConfiguration: Specs2RunConfiguration, location: Location[_ <: PsiElement], testClass: ScTypeDefinition, testName: String): Unit = {
    super.prepareRunConfiguration(runConfiguration, location, testClass, testName)

    // If the selected element is a non-empty string literal, we assume that this
    // is the name of an example to be filtered.
    if (testName != null) {
      val testNamePrefixed = s"${testClass.qualifiedName}::$testName"
      runConfiguration.setGeneratedName(testNamePrefixed)
      runConfiguration.setName(testNamePrefixed)
    }
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
    val suiteClasses = suitePaths.flatMap {
      parent.elementScope.getCachedClass(_)
    }
    if (suiteClasses.isEmpty) return false
    val suiteClazz = suiteClasses.head

    if (!ScalaPsiUtil.isInheritorDeep(parent, suiteClazz)) return false

    val (testClass, testName) = getTestClassWithTestName(location)
    if (testClass == null) return false
    val testClassPath = testClass.qualifiedName

    configuration match {
      case configuration: Specs2RunConfiguration =>
        configuration.testConfigurationData match {
          case testData: SingleTestData => testData.testClassPath == testClassPath && testData.testName == testName
          case classData: ClassTestData => classData.testClassPath == testClassPath && testName == null
          case _ => false
        }
      case _ => false
    }
  }

  private def extractStaticTestName(testDefExpr: ScInfixExpr): Option[String] = {
    testDefExpr.getChildren.toSeq
      .filterBy[ScExpression].headOption
      .flatMap(TestConfigurationUtil.getStaticTestName(_))
  }

  override def getTestClassWithTestName(location: Location[_ <: PsiElement]): (ScTypeDefinition, String) = {
    val element = location.getPsiElement
    val testClassDef: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (testClassDef == null) return (null, null)

    val suiteClasses = suitePaths.flatMap {
      element.elementScope.getCachedClass(_)
    }
    if (suiteClasses.isEmpty) return (null, null)
    val suiteClazz = suiteClasses.head // TODO: why head???
    if (!ScalaPsiUtil.isInheritorDeep(testClassDef, suiteClazz)) return (null, null)

    val testName = ScalaPsiUtil.getParentWithProperty(element, strict = false, e => TestNodeProvider.isSpecs2TestExpr(e)) match {
      case Some(infixExpr: ScInfixExpr) => extractStaticTestName(infixExpr).orNull
      case _                            => null
    }
    (testClassDef, testName)
  }
}
