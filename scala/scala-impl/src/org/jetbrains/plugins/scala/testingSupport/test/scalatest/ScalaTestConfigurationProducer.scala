package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution._
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer.CreateFromContextInfo
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer.CreateFromContextInfo._
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
import org.scalatest.finders.{Selection => TestFindersSelection}

final class ScalaTestConfigurationProducer extends AbstractTestConfigurationProducer[ScalaTestRunConfiguration] {

  override def getConfigurationFactory: ConfigurationFactory = ScalaTestConfigurationType.instance.confFactory

  override def suitePaths: Seq[String] = List("org.scalatest.Suite")

  override protected def configurationName(contextInfo: CreateFromContextInfo): String = contextInfo match {
    case AllInPackage(_, packageName)           =>
      ScalaBundle.message("test.in.scope.scalatest.presentable.text", packageName)
    case ClassWithTestName(testClass, testName) =>
      StringUtil.getShortName(testClass.qualifiedName) + testName.fold("")("." + _)
  }

  override def getTestClassWithTestName(location: PsiElementLocation): Option[ClassWithTestName] = {
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

    if (clazz == null) return None

    val templateBody: ScTemplateBody = clazz.extendsBlock.templateBody.orNull

    clazz = PsiTreeUtil.getTopmostParentOfType(clazz, classOf[ScTypeDefinition]) match {
      case null   => clazz
      case parent => parent
    }

    clazz match {
      case _: ScClass | _: ScTrait if matchesSomeTestSuite(clazz) =>
      case _ => return None
    }

    val maybeSelection = ScalaTestAstTransformer.testSelection(location)
    maybeSelection match {
      case Some(selection) =>
        val result1 = testClassWithTestNameForSelection(clazz, selection).map(t => ClassWithTestName(t._1, Option(t._2)))
        val result2 = result1.orElse(testClassWithTestNameForParent(location))
        result2
      case None =>
        val finder = new ScalaTestSingleTestLocationFinderOld(element, clazz, templateBody)
        Option(finder.testClassWithTestName).map(t => ClassWithTestName(t._1, Option(t._2)))
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

  private def testClassWithTestNameForParent(location: PsiElementLocation): Option[ClassWithTestName] =
    for {
      parent      <- Option(location.getPsiElement.getParent)
      newLocation = new PsiLocation(location.getProject, location.getModule, parent)
      result      <- getTestClassWithTestName(newLocation)
    } yield result
}

object ScalaTestConfigurationProducer {

  def instance: ScalaTestConfigurationProducer =
    RunConfigurationProducer.EP_NAME.findExtensionOrFail(classOf[ScalaTestConfigurationProducer])
}