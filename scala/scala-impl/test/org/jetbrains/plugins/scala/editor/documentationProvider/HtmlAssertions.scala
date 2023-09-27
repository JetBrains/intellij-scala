package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert._

trait HtmlAssertions {

  protected def assertDocHtml(
    expected: String,
    actual: String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.DontIgnore
  ): Unit =
    if (expected == null) {
      assertNull(actual)
    } else {
      val expectedNormalized = normalizeWhiteSpaces(expected, whitespacesMode)
      val actualNormalized = normalizeWhiteSpaces(actual, whitespacesMode)
      assertEquals(expectedNormalized, actualNormalized)
    }

  /**
   * The method "normalizes" html content in order it's easier to construct an expected HTML content.<br>
   * Without such normalization it can be hard to test HTML, because it can contain a lot of insignificant information.
   *
   * Under the hood the method:
   * 1. Removes new lines
   * 2. Collapses all whitespaces sequences to a single whitespace
   *
   * The exception is a `pre` tag, which preserves whitespaces inside.
   * The content in that tag is left untouched
   */
  private def normalizeWhiteSpaces(html: String, whitespacesMode: HtmlSpacesComparisonMode): String = {
    val buffer = new java.lang.StringBuilder

    val (preTagStart, preTagEnd) = ("<pre>", "</pre>")
    val preformattedRanges = MarkersUtils
      .findRanges(html, preTagStart, preTagEnd)
      .map(r => TextRange.create(r.getStartOffset, r.getEndOffset + preTagEnd.length))

    // adding two additional empty ranges to avoid handling special cases for first/last ranges
    val preformattedRangesFixed = TextRange.create(0, 0) +: preformattedRanges :+ TextRange.create(html.length, html.length)
    preformattedRangesFixed.sliding(2).foreach { case Seq(prev, cur) =>
      //Example: `content before <pre>content inside</pre>`
      val contentBeforePre = html.substring(prev.getEndOffset, cur.getStartOffset)
      val contentInsidePre = html.substring(cur.getStartOffset, cur.getEndOffset)

      import HtmlSpacesComparisonMode._
      val contentBeforePreNormalized = whitespacesMode match {
        case DontIgnore => contentBeforePre
        case DontIgnoreNewLinesCollapseSpaces => replaceWhitespacesWithSingleSpace(contentBeforePre)
        case IgnoreNewLinesAndCollapseSpaces => replaceWhitespacesWithSingleSpace(contentBeforePre.withoutNewLines)
      }

      buffer.append(contentBeforePreNormalized)
      buffer.append(contentInsidePre)
    }

    buffer.toString.trim
  }

  protected sealed trait HtmlSpacesComparisonMode
  protected object HtmlSpacesComparisonMode {
    object DontIgnore extends HtmlSpacesComparisonMode
    object DontIgnoreNewLinesCollapseSpaces extends HtmlSpacesComparisonMode
    object IgnoreNewLinesAndCollapseSpaces extends HtmlSpacesComparisonMode
  }

  private def replaceWhitespacesWithSingleSpace(contentBefore: String): String =
    contentBefore.replaceAll("[ \t]+", " ")

  implicit class StringOps(private val str: String) {
    def withoutNewLines: String = str
      .replace("\r", "")
      .replace("\n", "")
  }
}