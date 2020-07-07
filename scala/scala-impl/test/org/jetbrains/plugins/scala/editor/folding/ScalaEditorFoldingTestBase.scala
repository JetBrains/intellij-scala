package org.jetbrains.plugins.scala.editor.folding

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.folding.ScalaFoldingBuilder

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

abstract class ScalaEditorFoldingTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  val FOLD_START_MARKER_BEGIN = "<|fold["
  val FOLD_START_MARKER_END = "]>"
  val FOLD_END_MARKER = "</fold>"


  def ST(text: String) =
    FOLD_START_MARKER_BEGIN + text + FOLD_START_MARKER_END

  override val END = FOLD_END_MARKER

  val BLOCK_ST = ST("{...}")
  val PAR_ST = ST("(...)")
  val DOTS_ST = ST("...")
  val COMMENT_ST = ST("/.../")
  val DOC_COMMENT_ST = ST("/**...*/")
  val MLS_ST = ST("\"\"\"...\"\"\"")



  def genericCheckRegions(fileTextRaw: String): Unit = {
    val fileText = fileTextRaw.withNormalizedSeparator
    val expectedRegions = new ArrayBuffer[(TextRange, String)]()
    val textWithoutMarkers = new StringBuilder(fileText.length)
    val openMarkers = mutable.Stack[(Int, String)]()

    var pos = 0
    while ({
      val nextStartMarker = fileText.indexOf(FOLD_START_MARKER_BEGIN, pos)
      val nextEndMarker = fileText.indexOf(FOLD_END_MARKER, pos)

      Seq(nextStartMarker -> true, nextEndMarker -> false)
        .filter(_._1 >= 0)
        .sortBy(_._1) match {
        case (closestIdx, isStartMarker) +: _ =>
          textWithoutMarkers.append(fileText.substring(pos, closestIdx))
          val idxInTargetFile = textWithoutMarkers.length

          if (isStartMarker) {
            val replacementTextBeginIdx = closestIdx + FOLD_START_MARKER_BEGIN.length
            val replacementTextEndIdx = fileText.indexOf(FOLD_START_MARKER_END, replacementTextBeginIdx)
            assert(replacementTextEndIdx >= 0, "Expected end of start marker " + replacementTextBeginIdx)
            val replacementText = fileText.substring(replacementTextBeginIdx, replacementTextEndIdx)
            pos = replacementTextEndIdx + FOLD_START_MARKER_END.length

            openMarkers.push(idxInTargetFile -> replacementText)
          } else {
            assert(openMarkers.nonEmpty, "No more open markers for end marker at " + closestIdx)
            val (regionBegin, replacementText) = openMarkers.pop()
            val regionEnd = idxInTargetFile
            pos = closestIdx + FOLD_END_MARKER.length
            expectedRegions += (TextRange.create(regionBegin, regionEnd) -> replacementText)
          }
          true
        case Seq() =>
          false
      }
    }) ()

    assert(openMarkers.isEmpty, s"Unbalanced fold markers #3: ${openMarkers.mkString}")

    val assumedRegionRanges = expectedRegions.result().sortBy(_._1.getStartOffset)

    myFixture.configureByText("dummy.scala", textWithoutMarkers.result())

    val myBuilder = new ScalaFoldingBuilder
    val regions = myBuilder.buildFoldRegions(myFixture.getFile.getNode, myFixture getDocument myFixture.getFile)

    assert(regions.length == assumedRegionRanges.size, s"Different region count, expected: ${assumedRegionRanges.size}, but got: ${regions.length}")

    (regions zip assumedRegionRanges).zipWithIndex foreach {
      case ((region, (assumedRange, expectedPlaceholderText)), idx) =>
        assert(region.getRange.getStartOffset == assumedRange.getStartOffset,
          s"Different start offsets in region #$idx : expected ${assumedRange.getStartOffset}, but got ${region.getRange.getStartOffset}")
        assert(region.getRange.getEndOffset == assumedRange.getEndOffset,
          s"Different end offsets in region #$idx : expected ${assumedRange.getEndOffset}, but got ${region.getRange.getEndOffset}")
        val actualReplacementText = myBuilder.getLanguagePlaceholderText(region.getElement, region.getRange)
        assert(actualReplacementText == expectedPlaceholderText,
          s"Different placeholderTexts in region #$idx: expected ${expectedPlaceholderText}, but got ${actualReplacementText}")
    }
  }
}
