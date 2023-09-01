package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertEquals

abstract class ScalaHighlightConstructorInvocationUsagesTestBase
  extends ScalaLightCodeInsightFixtureTestCase {

  protected val start = MarkersUtils.startMarker
  protected val end = MarkersUtils.endMarker

  protected def doTest(fileText: String): Unit = {
    val (fileTextWithoutMarkers, expectedRanges) = MarkersUtils.extractMarker(fileText, caretMarker = Some(CARET))
    val file = myFixture.configureByText("dummy.scala", fileTextWithoutMarkers)
    val finalFileText = file.getText

    val editor = myFixture.getEditor
    HighlightUsagesHandler.invoke(myFixture.getProject, editor, file)

    val highlighters = editor.getMarkupModel.getAllHighlighters
    val actualRanges = highlighters.map(hr => TextRange.create(hr.getStartOffset, hr.getEndOffset)).toSeq

    val expected = rangeSeqToComparableString(expectedRanges, finalFileText)
    val actual = rangeSeqToComparableString(actualRanges, finalFileText)

    assertEquals(expected, actual)
  }

  private def rangeSeqToComparableString(ranges: Seq[TextRange], fileText: String): String =
    ranges.sortBy(_.getStartOffset).map { range =>
      val start = range.getStartOffset
      val end = range.getEndOffset
      s"($start, $end): " + fileText.substring(start, end)
    }.mkString("\n")
}
