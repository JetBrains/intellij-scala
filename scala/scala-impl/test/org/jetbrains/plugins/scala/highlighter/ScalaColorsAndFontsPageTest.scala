package org.jetbrains.plugins.scala.highlighter

import com.intellij.application.options.colors.highlighting.{HighlightData, HighlightsExtractor}
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter._
import org.junit.Assert.assertEquals
import org.junit.Test

import java.util
import java.util.ArrayList
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaColorsAndFontsPageTest {

  @Test
  def testPreviewContainsExamplesForEveryVisibleColorSchemeKey(): Unit = {
    val page = new ScalaColorsAndFontsPage
    val keysVisibleInSettings = page.getAttributeDescriptors.iterator.map(_.getKey).toSet

    val keysUsedInPreviewDemo = extractAttributeKeysUsedInTheDemoPreviewText(page)
    assertEquals(
      "Demo preview text should contain examples for every visible color scheme key",
      keysVisibleInSettings,
      keysUsedInPreviewDemo
    )
  }

  @Test
  def testPreviewContainsExamplesForRecentlyMissingColorSchemeKeys(): Unit = {
    val page = new ScalaColorsAndFontsPage

    val expectedKeys: Set[TextAttributesKey] = Set(
      BLOCK_COMMENT,
      ANNOTATION_ATTRIBUTE,
      XML_COMMENT,
      INTERPOLATED_STRING_INJECTION,
      LOCAL_VARIABLES,
      LOCAL_LAZY,
      VARIABLES,
      LAZY,
      BAD_CHARACTER,
      SCALATEST_KEYWORD,
    )

    assertEquals(Set.empty, expectedKeys.diff(extractAttributeKeysUsedInTheDemoPreviewText(page)))
  }

  private def extractAttributeKeysUsedInTheDemoPreviewText(page: ScalaColorsAndFontsPage): Set[TextAttributesKey] = {
    val highlights = new util.ArrayList[HighlightData]

    val extractor = new HighlightsExtractor(page.getAdditionalHighlightingTagToDescriptorMap)
    extractor.extractHighlights(page.getDemoText, highlights)

    highlights.asScala.map(_.getHighlightKey).toSet
  }
}
