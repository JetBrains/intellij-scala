package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert._

trait HtmlAssertions {

  protected def assertDocHtml(expected: String, actual: String): Unit =
    if (expected == null) {
      assertNull(actual)
    } else {
      val expectedNormalized = normalizeWhiteSpaces(expected)
      val actualNormalized = normalizeWhiteSpaces(actual)
      assertEquals(expectedNormalized, actualNormalized)
    }

  private val htmlTagRegex = """</?\w+\s?[^>]*>""".r
  private def removeSpacesAroundTags(html: String): String =
    html.replaceAll(s"(?m)\\s*($htmlTagRegex)\\s*", "$1")

  /**
   * Everywhere outside <pre> tag:
   * 1. Removes whitespaces tags
   * 2. Collapses all whitespaces (including new lines) sequences to a single whitespace
   *
   * This is done for an easier testing.
   * NOTE: we assume that whitespaces character only matter inside <pre> tag.
   */
  private def normalizeWhiteSpaces(htmlRaw: String): String = {
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

      buffer.append(removeSpacesAroundTags(contentBefore).replaceAll("\\s+", " "))
      buffer.append(preformattedContent)
    }

    buffer.toString.trim
  }
}
