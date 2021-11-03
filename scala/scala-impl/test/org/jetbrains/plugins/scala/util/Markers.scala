package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.EditorTestUtil
import junit.framework.TestCase
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert._

import scala.collection.immutable.SortedMap
import scala.collection.mutable

trait Markers {

  val caret = EditorTestUtil.CARET_TAG
  def startMarker(i: Int) = s"/*start$i*/"
  def endMarker(i: Int) = s"/*end$i*/"
  val startMarker = "/*start*/"
  val endMarker = "/*end*/"

  def start(i: Int = 0): String = startMarker(i)
  def end (i: Int = 0): String = endMarker(i)
  def start: String = startMarker
  def end: String = endMarker

  /**
   * @example
   * line /start/ 1 content /end/
   * line /start0/ /start1/ 2 /end1/ content/end0/
   */
  def extractNumberedMarkers(inputText: String): (String, Seq[TextRange]) = {
    val normalizedInput = inputText.withNormalizedSeparator

    def hasMarker(startName: String, endName: String): Boolean =
      normalizedInput.contains(startName) && normalizedInput.contains(endName)

    val hasNormalStartMarker = hasMarker(startMarker, endMarker)
    val numberedMarkers = LazyList.from(0).takeWhile(i => hasMarker(startMarker(i), endMarker(i)))

    val (resultText, ranges) = extractMarkers(
      normalizedInput,
      hasNormalStartMarker.fold(Seq((startMarker, endMarker)), Seq.empty) ++
        numberedMarkers.map(i => (startMarker(i), endMarker(i))),
      considerCaret = true
    )
    (resultText, ranges.map(_._1))
  }


  /**
   * Used to extract ranges that may be nested.
   *
   * @example
   * line /start/ 1 content /end/
   * line /start/ /start/ 1 /end/ content /start/ 2 /end/ /end/
   */
  def extractMarker(inputText: String,
                    startMarker: String = this.startMarker,
                    endMarker: String = this.endMarker,
                    considerCaret: Boolean = false,
                    caretText: String = this.caret): (String, Seq[TextRange]) = {
    val (resultText, ranges) = extractMarkers(inputText, Seq(startMarker -> endMarker), considerCaret, caretText)
    (resultText, ranges.map(_._1))
  }

  /**
   * Uuuuuuultimate function to extract ranges from an input sequence.
   * Multiple marker-kinds are supported and markers of different kinds
   * do not interfere with one another.
   * Markers of the same kind may be nested, but may not be interleaved.
   * (In fact they cannot be interleaved, now that I think about it).
   *
   * @param inputText       example: {{{
   *   line /start/ some content /start/ inner content /end/ some more content /end/
   *   line <foldStart> 2 </foldEnd> [[ content ]]
   * }}}
   * @param startEndMarkers example: {{{
   *   Seq(("/start/", "/end/"), ("<foldStart>", "<foldEnd>"), ("[[", "]]"))
   * }}}
   */
  def extractMarkers(inputText: String,
                     markers: Seq[(String, String)],
                     considerCaret: Boolean = false,
                     caretText: String = this.caret): (String, Seq[(TextRange, Int)]) = {
    val normalizedInput = inputText.withNormalizedSeparator

    val (ranges, idxAdjust) = markers.zipWithIndex
      .foldLeft((Seq.empty[(TextRange, Int)], (_: Int) => 0)) {
        case ((prevRanges, prevIdxAdjust), ((startMarker, endMarker), markerIdx)) =>
          val (ranges, idxAdjust) = findRangesAndAdjustment(normalizedInput, startMarker, endMarker)
          (prevRanges ++ ranges.map(_ -> markerIdx), i => prevIdxAdjust(i) + idxAdjust(i))
      }

    val caret = considerCaret.option(normalizedInput.indexOf(caretText)).filter(_ >= 0)
    def adjustIndexForMarkersAndCaret(i: Int): Int =
      i - idxAdjust(i) - caret.exists(i > _).fold(caretText.length, 0)

    val rangesFixed = ranges
      .sortBy{ case (TextRangeExt(s, e), _) => (s, -e) }
      .map {
        case (TextRangeExt(start, end), markerIdx) =>
          val newRange = TextRange.create(
            adjustIndexForMarkersAndCaret(start),
            adjustIndexForMarkersAndCaret(end),
          )
          (newRange, markerIdx)
      }

    val textFixed = markers.foldLeft(normalizedInput) {
      case (text, (startMarker, endMarker)) =>
        text
          .replace(startMarker, "")
          .replace(endMarker, "")
    }

    (textFixed, rangesFixed)
  }

  def findRanges(inputText: String, startMarker: String, endMarker: String): Seq[TextRange] =
    findRangesAndAdjustment(inputText, startMarker, endMarker)._1

  private def findRangesAndAdjustment(inputText: String, startMarker: String, endMarker: String): (Seq[TextRange], Int => Int) = {
    assertNoWindowsLineSeparator(inputText)

    val startReg = s"\\Q$startMarker\\E".r
    val endReg = s"\\Q$endMarker\\E".r

    val startIndexes = startReg.findAllMatchIn(inputText).map(_.start).toList
    val endIndexes = endReg.findAllMatchIn(inputText).map(_.start).toList
    assertEquals(
      s"start & end markers counts are not equal\nstart: $startIndexes,\nend: $endIndexes\ntext: $inputText",
      startIndexes.size,
      endIndexes.size
    )

    val allIndices = (startIndexes.map(_ -> true) ++ endIndexes.map(_ -> false)).sortBy(_._1)
    val rangesBuilder = Seq.newBuilder[TextRange]
    val unclosedRangeStarts = mutable.Stack.empty[Int]

    for ((idx, isStart) <- allIndices)
      if (isStart) unclosedRangeStarts.push(idx)
      else {
        val start = unclosedRangeStarts
          .removeHeadOption()
          .getOrElse(throw new AssertionError(s"No matching start marker for end marker at $idx"))
        rangesBuilder += TextRange.create(start, idx)
      }


    val ranges = rangesBuilder.result()
    ranges.foreach { range =>
      assertTrue("range end offset can't be smaller then start offset", range.getEndOffset >= range.getStartOffset)
    }

    // a sequence that holds at the index i the adjustment-amount
    // for all positions in the input text between the start offsets
    // of allIndices(i-1) and allIndices(i)
    // note that this list has one more element than allIndices,
    // because the last entry corresponds to the end of the inputtext
    val adjustmentsBeforeMarker =
      allIndices.foldLeft(List(0)) {
        case (prevs, (_, isStart)) =>
          prevs.head + isStart.fold(startMarker.length, endMarker.length) :: prevs
      }.reverse

    val adjustments =
      (allIndices.map(_._1) :+ inputText.length)
        .zip(adjustmentsBeforeMarker)
        .to(SortedMap)

    (ranges, adjustments.valuesIteratorFrom(_).next())
  }

  private def assertNoWindowsLineSeparator(text: String): Unit = {
    assertFalse(
      """Windows line separator '\r' detected in test data. Please normalise your test data using one of the following helpers:
        |  1. org.jetbrains.plugins.scala.extensions.StringExt.withNormalizedSeparator
        |  2. com.intellij.openapi.util.text.StringUtil.convertLineSeparators(java.lang.String)
        |  3. text.replace("\r", "")
        |""".stripMargin,
      text.contains("\r")
    )
  }
}

object MarkersUtils extends Markers


class MarkerUtilsTest extends TestCase with Markers with AssertionMatchers {
  def test_super_multi_nested(): Unit = {
    val code =
      """
        |<a></a>
        |<b>0</b>
        |<a><b></a></b>
        |<c><a>1<a>2<b><caret>3</a>4</b>5</a></c>
        |""".stripMargin

    val (result, ranges) = extractMarkers(
      code,
      Seq("a", "b", "c").map(c => (s"<$c>", s"</$c>")),
      considerCaret = true
    )

    result shouldBe
      """
        |
        |0
        |
        |12<caret>345
        |""".stripMargin

    ranges shouldBe Seq(
      // <a></a>
      TextRange.create(1, 1) -> 0,
      // <b>0</b>
      TextRange.create(2, 3) -> 1,
      // <a><b></a></b>
      TextRange.create(4, 4) -> 0,
      TextRange.create(4, 4) -> 1,
      // <c><a>1<a>2<b><caret>3</a>4</b>5</a></c>
      TextRange.create(5, 10) -> 2,
      TextRange.create(5, 10) -> 0,
      TextRange.create(6, 8) -> 0,
      TextRange.create(7, 9) -> 1,
    )
  }
}