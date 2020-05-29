package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert._

trait DocumentationTesting {

  protected val | : String = EditorTestUtil.CARET_TAG

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

  protected def assertDocHtml(expected: String, actual: String): Unit =
    if (expected == null) {
      assertNull(actual)
    } else {
      // new lines are used in tests for the visual readability of HTML
      val expectedNormalized = normalizeNewLines(expected)
      val actualNormalized = normalizeNewLines(actual)
      assertEquals(expectedNormalized, actualNormalized)
    }

  private val htmlTagRegex = """</?\w+\s?[^>]*>""".r
  private def removeSpacesAroundTags(html: String): String =
    html.replaceAll(s"(?m)\\s*($htmlTagRegex)\\s*", "$1")

  private def normalizeNewLines(htmlRaw: String): String = {
    val html = htmlRaw.replace("\r", "")

    val buffer = new java.lang.StringBuilder

    val (preTagStart, preTagEnd) = ("<pre>", "</pre>")
    val preformattedRanges = MarkersUtils
      .findRanges(html, preTagStart, preTagEnd)
      .map(r => TextRange.create(r.getStartOffset, r.getEndOffset + preTagEnd.length))

    // adding two additional empty ranges to avoid handling special cases for first/last ranges
    val preformattedRangesFixed = TextRange.create(0, 0) +: preformattedRanges :+ TextRange.create(html.length, html.length)
    preformattedRangesFixed.sliding(2).foreach { case Seq(prev, cur) =>
      val contentBefore = html.substring(prev.getEndOffset, cur.getStartOffset)
      val preformattedContent = html.substring(cur.getStartOffset, cur.getEndOffset)
      buffer.append(removeSpacesAroundTags(contentBefore))
      buffer.append(preformattedContent)
    }

    buffer.toString.trim
  }
}
