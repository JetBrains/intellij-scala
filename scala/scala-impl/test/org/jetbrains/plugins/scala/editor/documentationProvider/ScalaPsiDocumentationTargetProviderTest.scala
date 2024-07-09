package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.{assertEquals, assertTrue, fail}

import scala.jdk.CollectionConverters.ListHasAsScala

class ScalaPsiDocumentationTargetProviderTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  def testImportCaseClassWithoutCompanionObject(): Unit = {
    addScalaFileToProject("MyCaseClass.scala",
      """package org.example
        |
        |case class MyCaseClass()
        |""".stripMargin
    )

    doTest(
      s"""import org.example.${CARET}MyCaseClass""",
      """(21,45) org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScClassImpl"""
    )
  }

  def testImportCaseClassWithCompanionObject(): Unit = {
    addScalaFileToProject("MyCaseClass.scala",
      """package org.example
        |
        |case class MyCaseClass()
        |
        |object MyCaseClass
        |""".stripMargin
    )

    doTest(
      s"""import org.example.${CARET}MyCaseClass""",
      """(21,45) org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScClassImpl
        |(47,65) org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScObjectImpl
        |""".stripMargin
    )
  }

  def testImportOverloadedDefinitions(): Unit = {
    addScalaFileToProject("MyCaseClass.scala",
      """object Definitions {
        |  def foo: String = ???
        |  def foo(x: Int): String = ???
        |  def foo(x: Int, y: String): String = ???
        |}
        |  """.stripMargin
    )

    doTest(
      s"""import Definitions.${CARET}foo""",
      """(79,119) org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
        |(47,76) org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
        |(23,44) org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
        |""".stripMargin
    )
  }

  //noinspection UnstableApiUsage
  private def doTest(
    @Language("Scala") fileText: String,
    expectedTargetsText: String
  ): Unit = {
    configureScalaFromFileText(fileText)

    val documentationTargetProvider = IdeDocumentationTargetProvider.getInstance(getProject)
    val targets = documentationTargetProvider.documentationTargets(getEditor, getFile, getEditor.getCaretModel.getOffset).asScala
    val targetElements = targets.map {
      case t: PsiElementDocumentationTarget =>
        t.getTargetElement
      case unexpected =>
        fail(s"Unexpected target class ${unexpected.getClass}").asInstanceOf[Nothing]
    }

    def testRepresentation(e: PsiElement): String =
      s"${e.getTextRange} ${e.getClass.getName}"

    assertEquals(
      expectedTargetsText.trim,
      targetElements.map(testRepresentation).mkString("\n")
    )

    targetElements.foreach { e =>
      assertTrue(s"""Target element must be physical (${e.getClass}): ${e.getText}""".stripMargin, e.isPhysical)
    }
  }
}