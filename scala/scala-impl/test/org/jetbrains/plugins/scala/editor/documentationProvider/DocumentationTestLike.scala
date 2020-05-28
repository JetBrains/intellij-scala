package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil
import org.junit.Assert.{assertEquals, assertNull, _}

trait DocumentationTestLike {

  protected val | : String = EditorTestUtil.CARET_TAG

  // TODO: (!!!) this contains <pre> so definition should be tested without ignoring new lines
  protected val DocStart = "<html><body>"
  protected val DocEnd   = "</body></html>"
  protected val DefinitionStart: String = DocumentationMarkup.DEFINITION_START
  protected val DefinitionEnd  : String = DocumentationMarkup.DEFINITION_END
  protected val ContentStart   : String = DocumentationMarkup.CONTENT_START
  protected val ContentEnd     : String = DocumentationMarkup.CONTENT_END
  protected val SectionsStart  : String = DocumentationMarkup.SECTIONS_START
  protected val SectionsEnd    : String = DocumentationMarkup.SECTIONS_END
  protected val EmptySections = s"$SectionsStart<p>$SectionsEnd"

  protected def createEditorAndFile(fileContent: String): (Editor, PsiFile)

  protected def generateDoc(editor: Editor, file: PsiFile): String

  protected final def generateDoc(fileContent: String): String = {
    val (editor, file) = createEditorAndFile(fileContent)
    assertTrue("file should contain valid psi tree", file.isValid)
    generateDoc(editor, file)
  }

  protected def doGenerateDocTest(fileContent: String, expectedDoc: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    assertDocHtml(expectedDoc, actualDoc)
  }
  // TODO: rename to doGenerateDocShortTest
  protected def doShortGenerateDocTest(fileContent: String, expectedDocShort: String): Unit = {
    val expectedDoc = s"<html><body>${expectedDocShort.trim}</body></html>"
    doGenerateDocTest(fileContent, expectedDoc)
  }

  // TODO: refactor this shit naming
  protected def doGenerateDocWithoutDefinitionTest(fileContent: String, expectedContent: String): Unit = {
    import DocumentationMarkup._

    val actualDoc = generateDoc(fileContent)

    val definitionTagEnd = actualDoc.indexOf(DEFINITION_END)
    assertTrue(s"Can't find definition section in the generated doc:\n$actualDoc", definitionTagEnd >= 0)
    val bodyEnd = actualDoc.indexOf("</body></html>")
    assertTrue(s"Can't find body tag in the generated doc:\n$actualDoc", bodyEnd >= 0)

    val actualContent = actualDoc.substring(definitionTagEnd + DEFINITION_END.length, bodyEnd)

    assertDocHtml(expectedContent, actualContent)
  }

  protected def assertDocHtml(expected: String, actual: String): Unit =
    if (expected == null) {
      assertNull(actual)
    } else {
      // new lines are used in tests for the visual readability of HTML
      val expectedNormalized = normalizeHtml(expected)
      val actualNormalized = normalizeHtml(actual)
      assertEquals(expectedNormalized, actualNormalized)
    }

  protected def normalizeHtml(html: String): String =
    html.trim.replaceAll("[\r]", "").replaceAll("[\n]", "")
}
