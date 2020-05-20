package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil
import org.junit.Assert._
import org.junit.Assert.{assertEquals, assertNull}

trait DocumentationTestLike {

  protected def | : String = EditorTestUtil.CARET_TAG

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

  protected def doShortGenerateDocTest(fileContent: String, expectedDocShort: String): Unit = {
    val expectedDoc = s"<html><body>${expectedDocShort.trim}</body></html>"
    doGenerateDocTest(fileContent, expectedDoc)
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
