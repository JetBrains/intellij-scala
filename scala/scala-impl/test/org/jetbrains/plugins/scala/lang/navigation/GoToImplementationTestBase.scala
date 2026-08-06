package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertTrue

abstract class GoToImplementationTestBase extends GoToTestBase {

  protected final def doGoToImplementationTest(text: String): Unit = {
    val (textWithoutMarkers, expectedRanges) =
      MarkersUtils.extractMarker(text.withNormalizedSeparator.trim, START, END, caretMarker = Some(CARET))
    configureFromFileText(textWithoutMarkers)

    val gotoData = CodeInsightTestUtil.gotoImplementation(getEditor, getFile)
    val actualRanges = gotoData.targets.toSeq.map(_.getTextRange)
    val expectedRangesNotFound = expectedRanges.filterNot(actualRanges.contains)

    assertTrue(
      s"Targets not found for source: ${gotoData.source.getText}",
      actualRanges.nonEmpty
    )
    assertTrue(
      s"""Targets found at:
         |${rangesDebugText(actualRanges, textWithoutMarkers)}
         |not found:
         |${rangesDebugText(expectedRangesNotFound, textWithoutMarkers)}""".stripMargin,
      expectedRangesNotFound.isEmpty
    )
    assertTrue(
      s"""Found too many targets:
         |${rangesDebugText(actualRanges, textWithoutMarkers)}
         |expected:
         |${rangesDebugText(expectedRanges, textWithoutMarkers)}""".stripMargin,
      actualRanges.lengthIs == expectedRanges.length
    )
  }

  private def rangesDebugText(ranges: Seq[TextRange], fileText: String): String = {
    val rangeTexts = ranges.map(rangeDebugText(_, fileText))
    rangeTexts.mkString("  ", "\n  ", "")
  }

  private def rangeDebugText(range: TextRange, fileText: String): String = {
    val rangeText = fileText.substring(range.getStartOffset, range.getEndOffset)
    s"$range[$rangeText]"
  }
}
