package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.sbt.language.SbtFileType
import org.junit.Assert
import org.junit.Assert._

abstract class SbtDocumentationProviderTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  protected val description = """This is a description for some-key"""

  protected val wrapperHtmlReg = """^\s*<html><body>.+?</pre>(.+)</body></html>\s*$""".r

  protected def doFullTest(sbtContent: String, expectedDoc: String): Unit = {
    val actualDoc = generateDoc(sbtContent)
    assertDocHtml(expectedDoc, actualDoc)
  }

  protected def doShortTest(sbtContent: String, expectedDocShort: String): Unit = {
    val actualDoc      = generateDoc(sbtContent)
    val actualDocShort = actualDoc match {
      case wrapperHtmlReg(inner) => inner
      case null                  => fail("No documentation is returned").asInstanceOf[Nothing]
      case _                     => fail(s"couldn't extract short documentation from text:\n$actualDoc ").asInstanceOf[Nothing]
    }
    assertDocHtml(s"<br/><b>$expectedDocShort</b>", actualDocShort)
  }

  protected def assertDocHtml(expected: String, actual: String): Unit =
    if(expected == null) {
      assertNull(actual)
    } else {
      val expectedNormalized = expected.replaceAll("[\n\r]", "")
      assertEquals(expectedNormalized, actual.trim)
    }

  protected def generateDoc(sbtContent: String): String = {
    val file = createSbtFile(sbtContent)
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(file)
    val documentationProvider = new SbtDocumentationProvider
    documentationProvider.generateDoc(referredElement, elementAtCaret)
  }

  protected def createSbtFile(sbtContent: String): PsiFile = {
    val fileText =
      s"""import sbt._
         |import sbt.KeyRanks._
         |$sbtContent
         |""".stripMargin.withNormalizedSeparator
    getFixture.configureByText(SbtFileType, fileText)
  }

  /** see [[com.intellij.lang.documentation.DocumentationProvider#generateDoc]] parameters */
  protected def extractReferredAndOriginalElements(file: PsiFile): (PsiElement, PsiElement) = {
    def fail: Nothing = Assert.fail("No appropriate original element found at caret position").asInstanceOf[Nothing]

    val elementAtCaret = file.findElementAt(myFixture.getEditor.getCaretModel.getOffset)
    elementAtCaret.parentOfType(classOf[ScNamedElement]) match {
      case Some(definition) =>
        (definition, definition) // if caret is placed at a the key definition itself
      case None =>
        elementAtCaret.parentOfType(classOf[ScReferenceExpression]) match {
          case Some(reference) =>
            (reference.resolve(), reference) // if caret is placed at a reference to the key definition
          case None => fail
        }
    }
  }
}
