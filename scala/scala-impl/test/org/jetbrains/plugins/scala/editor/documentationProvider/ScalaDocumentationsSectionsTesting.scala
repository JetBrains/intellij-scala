package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert._

trait ScalaDocumentationsSectionsTesting {
  self: DocumentationTesting =>

  protected val DocStart = "<html><body>"
  protected val DocEnd   = "</body></html>"
  protected val DefinitionStart: String = DocumentationMarkup.DEFINITION_START
  protected val DefinitionEnd  : String = DocumentationMarkup.DEFINITION_END
  protected val ContentStart   : String = DocumentationMarkup.CONTENT_START
  protected val ContentEnd     : String = DocumentationMarkup.CONTENT_END
  protected val SectionsStart  : String = DocumentationMarkup.SECTIONS_START
  protected val SectionsEnd    : String = DocumentationMarkup.SECTIONS_END
  protected val EmptySectionsContent = "<p>"
  protected val EmptySections = s"$SectionsStart<p>$SectionsEnd"

  protected def doGenerateDocBodyTest(fileContent: String, expectedDoc: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    assertDocHtml2(DocStart + expectedDoc + DocEnd, actualDoc)
  }

  protected def doGenerateDocDefinitionTest(fileContent: String, expectedDefinition: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualSections = extractSection(actualDoc, "definition", DefinitionStart, DefinitionEnd)
    assertDocHtml2(DefinitionStart + expectedDefinition + DefinitionEnd, actualSections)
  }

  protected def doGenerateDocSectionsTest(fileContent: String, expectedDoSections: String): Unit = {
    val actualDoc = generateDoc(fileContent)
    val actualSections = extractSection(actualDoc, "sections", SectionsStart, SectionsEnd)
    assertDocHtml2(SectionsStart + expectedDoSections + SectionsEnd, actualSections)
  }

  // NOTE: doesn't support nested tags,
  // so will not detect "<div>a <div> b </div></div>"
  // will find first closing tag: "<div>a <div> b </div>"
  private def extractSection(doc: String, sectionName: String, tagStart: String, tagEnd: String): String =
    doc.indexOf(tagStart) match {
      case -1 => fail(s"no '$sectionName' section found\n$doc").asInstanceOf[Nothing]
      case idx  => doc.substring(idx, doc.indexOf(tagEnd, idx)) + tagEnd
    }

  protected def expectedBody(definition: String, content: String, sections: String = EmptySectionsContent): String =
    s"$DefinitionStart$definition$DefinitionEnd" +
      s"$ContentStart$content$ContentEnd" +
      s"$SectionsStart$sections$SectionsEnd"

  private def assertDocHtml2(expected: String, actual: String): Unit =
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


