package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertNull}

abstract class DocumentationProviderTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  protected def documentationProvider: DocumentationProvider

  protected def createFile(fileContent: String): PsiFile

  protected def | = CARET

  protected def doTest(fileContent: String, expectedDoc: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    assertDocHtml(expectedDoc, actualDoc)
  }

  protected def doShortTest(fileContent: String, expectedDocShort: String): Unit = {
    val expectedDoc = s"<html><body>${expectedDocShort.trim}</body></html>"
    doTest(fileContent, expectedDoc)
  }

  protected def assertDocHtml(expected: String, actual: String): Unit =
    if (expected == null) {
      assertNull(actual)
    } else {
      // new lines are used in tests for the visual readability of HTML
      val expectedNormalized = expected.replaceAll("[\n\r]", "")
      val actualNormalized = actual.trim.replaceAll("[\n\r]", "")
      assertEquals(expectedNormalized, actualNormalized)
    }

  protected def generateDoc(fileContent: String): String = {
    val file = createFile(fileContent)
    generateDoc(file)
  }

  private def generateDoc(file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(file)
    documentationProvider.generateDoc(referredElement, elementAtCaret)
  }

  /** see parameters of [[com.intellij.lang.documentation.DocumentationProvider#generateDoc]] */
  protected def extractReferredAndOriginalElements(file: PsiFile): (PsiElement, PsiElement) = {
    val elementAtCaret = file.findElementAt(myFixture.getEditor.getCaretModel.getOffset)
    elementAtCaret.parentOfType(classOf[ScNamedElement]) match {
      case Some(definition) => // if caret is placed at a the key definition itself
        (definition, definition)
      case None =>
        elementAtCaret.parentOfType(classOf[ScReferenceExpression]) match {
          case Some(reference) => // if caret is placed at a reference to the key definition
            val resolved = reference.resolve()
            (resolved, reference)
          case None =>
            Assert.fail("No appropriate original element found at caret position").asInstanceOf[Nothing]
        }
    }
  }
}
