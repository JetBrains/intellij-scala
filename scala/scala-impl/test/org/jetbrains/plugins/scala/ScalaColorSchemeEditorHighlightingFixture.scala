package org.jetbrains.plugins.scala

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.plugins.scala.ScalaColorSchemeEditorHighlightingFixture.ExpectedHighlight
import org.junit.Assert.assertTrue

import scala.jdk.CollectionConverters.CollectionHasAsScala

final class ScalaColorSchemeEditorHighlightingFixture(fixture: JavaCodeInsightTestFixture) {
  def assertHighlights(
    source: String,
    expectedHighlights: ExpectedHighlight*
  ): Unit = {
    fixture.configureByText("Example.scala", source)
    val highlights = fixture.doHighlighting().asScala.toSeq

    expectedHighlights.foreach { expected =>
      val startOffset = ScalaColorSchemeEditorHighlightingFixture.findOccurrence(source, expected)
      assertTrue(
        s"The source must contain occurrence ${expected.occurrence} of '${expected.text}'",
        startOffset >= 0
      )

      val endOffset = startOffset + expected.text.length
      val found = highlights.exists { info =>
        textAttributesKey(info) == expected.key &&
          info.getStartOffset <= startOffset &&
          endOffset <= info.getEndOffset
      }

      assertTrue(
        s"No editor highlight for '${expected.text}' with ${expected.key}. Actual highlights: ${formatHighlights(highlights, source)}",
        found
      )
    }
  }

  private def textAttributesKey(info: HighlightInfo): TextAttributesKey =
    Option(info.forcedTextAttributesKey).getOrElse(info.`type`.getAttributesKey)

  private def formatHighlights(highlights: Seq[HighlightInfo], source: String): String =
    highlights.map { info =>
      val highlightedText = source.substring(info.getStartOffset, info.getEndOffset)
      s"'$highlightedText' (${textAttributesKey(info)})"
    }.mkString(", ")
}

object ScalaColorSchemeEditorHighlightingFixture {
  final case class ExpectedHighlight(
    text: String,
    key: TextAttributesKey,
    occurrence: Int = 0
  )

  def findOccurrence(source: String, expected: ExpectedHighlight): Int = {
    require(expected.occurrence >= 0, "occurrence must be non-negative")

    var nextOffset = 0
    for (_ <- 0 to expected.occurrence) {
      val occurrenceOffset = source.indexOf(expected.text, nextOffset)
      if (occurrenceOffset < 0)
        return -1
      nextOffset = occurrenceOffset + expected.text.length
    }
    nextOffset - expected.text.length
  }
}
