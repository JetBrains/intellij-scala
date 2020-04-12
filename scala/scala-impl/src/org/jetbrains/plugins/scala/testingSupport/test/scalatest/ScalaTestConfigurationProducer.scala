package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution._
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationTypeUtil}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
import org.scalatest.finders.{Selection => TestFindersSelection}

final class ScalaTestConfigurationProducer extends AbstractTestConfigurationProducer[ScalaTestRunConfiguration] {

  override def getConfigurationFactory: ConfigurationFactory = {
    val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[ScalaTestConfigurationType])
    configurationType.confFactory
  }

  override def suitePaths: Seq[String] = List("org.scalatest.Suite")

  override protected def configurationNameForPackage(packageName: String): String =
    ScalaBundle.message("test.in.scope.scalatest.presentable.text", packageName)

  override protected def configurationName(testClass: ScTypeDefinition, testName: String): String =
    StringUtil.getShortName(testClass.qualifiedName) + (if (testName == null) "" else "." + testName)

  // TODO: avoid nulls
  override def getTestClassWithTestName(location: PsiElementLocation): (ScTypeDefinition, String) = {
    val element = location.getPsiElement

    def matchesSomeTestSuite(typ: ScTemplateDefinition): Boolean = suitePaths.exists(isInheritor(typ, _))

    var clazz: ScTypeDefinition = element match {
      case file: ScalaFile =>
        file.typeDefinitions.filter(matchesSomeTestSuite) match {
          case Seq(testClass) => testClass // run multiple test classes in a file is not supported yet, see SCL-15567
          case _ => null
        }
      case _ =>
        PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    }

    def nullResult: (Null, Null) = (null, null) // TODO: eliminate nulls in testingSupport package

    if (clazz == null) return nullResult

    val templateBody: ScTemplateBody = clazz.extendsBlock.templateBody.orNull

    clazz = PsiTreeUtil.getTopmostParentOfType(clazz, classOf[ScTypeDefinition]) match {
      case null   => clazz
      case parent => parent
    }

    clazz match {
      case _: ScClass | _: ScTrait if matchesSomeTestSuite(clazz) =>
      case _ => return nullResult
    }

    ScalaTestAstTransformer.testSelection(location) match {
      case Some(selection) =>
        val result1 = testClassWithTestNameForSelection(clazz, selection)
        val result2 = result1.orElse(testClassWithTestNameForParent(location))
        result2.getOrElse(nullResult)
      case None =>
        val finder = new ScalaTestSingleTestLocationFinderOld(element, clazz, templateBody)
        finder.testClassWithTestName
    }
  }

  private def testClassWithTestNameForSelection(
    clazz: ScTypeDefinition,
    selection: TestFindersSelection
  ): Option[(ScTypeDefinition, String)] =
    if (selection.testNames.nonEmpty) {
      val testNames = selection.testNames.toSeq.map(_.trim)
      val testNamesConcat = testNames.mkString("\n")
      Some((clazz, testNamesConcat))
    } else {
      None
    }

  private def testClassWithTestNameForParent(location: PsiElementLocation): Option[(ScTypeDefinition, String)] =
    for {
      parent      <- Option(location.getPsiElement.getParent)
      newLocation = new PsiLocation(location.getProject, parent)
      result      <- Option(getTestClassWithTestName(newLocation))
    } yield result
}
