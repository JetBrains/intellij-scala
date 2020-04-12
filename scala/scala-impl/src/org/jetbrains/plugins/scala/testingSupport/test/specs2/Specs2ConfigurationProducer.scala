package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationTypeUtil}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationUtil}

final class Specs2ConfigurationProducer extends AbstractTestConfigurationProducer[Specs2RunConfiguration] {

  override def getConfigurationFactory: ConfigurationFactory = {
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

  override protected def prepareRunConfiguration(runConfiguration: Specs2RunConfiguration, location: PsiElementLocation, testClass: ScTypeDefinition, testName: String): Unit = {
    super.prepareRunConfiguration(runConfiguration, location, testClass, testName)

    // If the selected element is a non-empty string literal, we assume that this
    // is the name of an example to be filtered.
    if (testName != null) {
      val testNamePrefixed = s"${testClass.qualifiedName}::$testName"
      runConfiguration.setGeneratedName(testNamePrefixed)
      runConfiguration.setName(testNamePrefixed)
    }
  }

  override protected def isClassOfTestConfigurationFromLocation(configuration: Specs2RunConfiguration, location: PsiElementLocation): Boolean = {
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(location.getPsiElement, classOf[ScTypeDefinition], false)
    if (parent == null) return false
    val suiteClasses = suitePaths.flatMap(parent.elementScope.getCachedClass)
    if (suiteClasses.isEmpty) return false
    val suiteClazz = suiteClasses.head // TODO: why head?

    if (ScalaPsiUtil.isInheritorDeep(parent, suiteClazz)) {
      super.isClassOfTestConfigurationFromLocation(configuration, location)
    } else {
      false
    }
  }

  private def extractStaticTestName(testDefExpr: ScInfixExpr): Option[String] =
    testDefExpr.getChildren.toSeq
      .filterBy[ScExpression]
      .headOption
      .flatMap(TestConfigurationUtil.getStaticTestName(_))

  override def getTestClassWithTestName(location: PsiElementLocation): (ScTypeDefinition, String) = {
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
