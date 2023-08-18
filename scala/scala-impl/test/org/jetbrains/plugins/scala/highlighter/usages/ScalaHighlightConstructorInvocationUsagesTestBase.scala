package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.Markers
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

abstract class ScalaHighlightConstructorInvocationUsagesTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with AssertionMatchers
    with Markers {

  protected val | = CARET
  protected val |< = startMarker
  protected val >| = endMarker

  protected def doTest(fileText: String): Unit = {
    val (fileTextWithoutMarkers, expectedRanges) = extractMarker(fileText, caretMarker = Some(CARET))
    val file = myFixture.configureByText("dummy.scala", fileTextWithoutMarkers)
    val finalFileText = file.getText

    val editor = myFixture.getEditor
    HighlightUsagesHandler.invoke(myFixture.getProject, editor, file)

    val highlighters = editor.getMarkupModel.getAllHighlighters
    val actualRanges = highlighters.map(hr => TextRange.create(hr.getStartOffset, hr.getEndOffset)).toSeq

    val expected = rangeSeqToComparableString(expectedRanges, finalFileText)
    val actual = rangeSeqToComparableString(actualRanges, finalFileText)

    actual shouldBe expected
  }

  private def rangeSeqToComparableString(ranges: Seq[TextRange], fileText: String): String =
    ranges.sortBy(_.getStartOffset).map { range =>
      val start = range.getStartOffset
      val end = range.getEndOffset
      s"($start, $end): " + fileText.substring(start, end)
    }.mkString("\n")
}
