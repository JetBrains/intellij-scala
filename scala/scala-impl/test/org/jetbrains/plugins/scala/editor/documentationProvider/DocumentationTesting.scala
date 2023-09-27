package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil
import org.junit.Assert._

trait DocumentationTesting extends HtmlAssertions {

  protected val | : String = EditorTestUtil.CARET_TAG

  protected def createEditorAndFile(fileContent: String): (Editor, PsiFile)

  protected def generateDoc(editor: Editor, file: PsiFile): String

  protected final def generateDoc(fileContent: String): String = {
    val (editor, file) = createEditorAndFile(fileContent)
    assertTrue("file should contain valid psi tree", file.isValid)
    generateDoc(editor, file)
  }

  protected def doGenerateDocTest(
    fileContent: String,
    expectedDoc: => String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = generateDoc(fileContent)
    assertDocHtml(expectedDoc, actualDoc, whitespacesMode)
  }
}
