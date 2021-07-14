package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert._

import scala.annotation.tailrec
import scala.collection.immutable.SortedMap
import scala.collection.mutable

trait Markers {

  private[this] val caretText = "<caret>"
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
    var input = inputText
    val caretPosition = inputText.indexOf(caretText)
    def removeCaret(index: Int): Int =
      if (caretPosition >= 0 && index > caretPosition) index - caretText.length else index
    val markerPositions = mutable.Map[String, Int]()
    val textRanges = mutable.Buffer.empty[TextRange]

    def detectMarker(start: String, end: String): Boolean = {
      input.contains(start) && input.contains(end) && {
        markerPositions.put(start, removeCaret(input.indexOf(start)))
        markerPositions.put(end, removeCaret(input.indexOf(end)))
        true
      }
    }
    @tailrec
    def detectNumberedMarkers(i: Int): Int =
      if (detectMarker(startMarker(i), endMarker(i))) detectNumberedMarkers(i + 1) else i

    val hasStartMarker = detectMarker(startMarker, endMarker)
    val index = detectNumberedMarkers(0)
    val adjustedMarkers = sortMarkers(markerPositions.toList.sortBy(_._2)).toMap

    def addRange(start: String, end: String): Unit = {
      input = input.replace(start, "")
      input = input.replace(end, "")
      textRanges += new TextRange(adjustedMarkers(start), adjustedMarkers(end))
    }

    if (hasStartMarker)
      addRange(startMarker, endMarker)
    for (i <- 0 until index) addRange(startMarker(i), endMarker(i))

    input -> textRanges.toSeq
  }

  private def sortMarkers(sorted: List[(String, Int)], offset: Int = 0): List[(String, Int)] = {
    sorted match {
      case (marker, position) :: tail => (marker, position - offset) :: sortMarkers(tail, offset + marker.length)
      case _ => sorted
    }
  }



  /**
   * Used to extract ranges that do not intersect
   *
   * @example
   * line /start/ 1 content /end/
   * line /start/ 2 /end/ /start/ content /end/
   *
   * TODO: reuse extractSequentialMarkers generic implementation
   */
  def extractNestedMarkers(inputText: String,
                           startMarker: String = this.startMarker,
                           endMarker: String = this.endMarker,
                           considerCaret: Boolean = false,
                           caretText: String = this.caretText): (String, Seq[TextRange]) = {
    val (ranges, idxAdjust) = findRangesAndAdjustment(inputText, startMarker, endMarker)
    val caret = considerCaret.option(inputText.indexOf(caretText)).filter(_ >= 0)
    def adjustIndexForMarkersAndCaret(i: Int): Int = {
      val adjustedIdx = idxAdjust(i)
      i - adjustedIdx - caret.exists(i > _).fold(caretText.length, 0)
    }

    val rangesFixed = ranges.map {
      case TextRangeExt(start, end) =>
        TextRange.create(
          adjustIndexForMarkersAndCaret(start),
          adjustIndexForMarkersAndCaret(end),
        )
    }

    val textFixed = inputText
      .replace(startMarker, "")
      .replace(endMarker, "")

    (textFixed, rangesFixed)
  }

  def findRanges(inputText: String, startMarker: String, endMarker: String): Seq[TextRange] =
    findRangesAndAdjustment(inputText, startMarker, endMarker)._1

  private def findRangesAndAdjustment(inputText: String, startMarker: String, endMarker: String): (Seq[TextRange], Int => Int) = {
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
    val starterStack = mutable.Stack.empty[Int]

    for ((idx, isStart) <- allIndices)
      if (isStart) starterStack.push(idx)
      else {
        val start = starterStack
          .removeHeadOption()
          .getOrElse(throw new AssertionError(s"No matching start marker for end marker at $idx"))
        rangesBuilder += TextRange.create(start, idx)
      }


    val ranges = rangesBuilder.result()
    ranges.foreach { range =>
      assertTrue("range end offset can't be smaller then start offset", range.getEndOffset >= range.getStartOffset)
    }

    val adjustmentsAfterIdx =
      allIndices.scanLeft(0) {
        case (prev, (_, isStart)) =>
          prev + isStart.fold(startMarker.length, endMarker.length)
      }.tail

    val adjustments =
      (allIndices.map(_._1) :+ inputText.length)
        .zip(0 +: adjustmentsAfterIdx)
        .to(SortedMap)

    (ranges, adjustments.valuesIteratorFrom(_).next())
  }
  /**
   * Used to extract ranges that do not do not intersect, support
   *
   * @param inputText       example: {{{
   *   line /start/ 1 content /end/
   *   line <foldStart> 2 </foldEnd> [[ content ]]
   * }}}
   * @param startEndMarkers example: {{{
   *   Seq(("/start/", "/end/"), ("<foldStart>", "<foldEnd>"), ("[[", "]]"))
   * }}}
   */
  def extractSequentialMarkers(inputText: String, startEndMarkers: Seq[(String, String)]): (String, Seq[(TextRange, Int)]) = {
    type MarkerType = Int

    // marker selection ranges with marker types
    val ranges: Seq[(TextRange, MarkerType)] = startEndMarkers.zipWithIndex.flatMap {
      case ((startMarker, endMarker), markerType) =>
        val startReg = s"\\Q$startMarker\\E".r
        val endReg   = s"\\Q$endMarker\\E".r

        val startMatches = startReg.findAllMatchIn(inputText).map(m => TextRange.create(m.start, m.end)).toSeq
        val endMatches   = endReg.findAllMatchIn(inputText).map(m => TextRange.create(m.start, m.end)).toSeq

        assertEquals(
          s"start & end markers ($startMarker / $endMarker) counts are not equal\nstart: $startMatches,\nend: $endMatches",
          startMatches.size,
          endMatches.size
        )

        val ranges = startMatches.zip(endMatches).map { case (s, e) => TextRange.create(s.getStartOffset, e.getStartOffset) }
        ranges.foreach { range =>
          assertTrue("range end offset can't be smaller then start offset", range.getEndOffset >= range.getStartOffset)
        }

        ranges.map(r => (r, markerType))
    }

    assertNonIntersecting(ranges.map(_._1))

    val rangesFixed = ranges.foldLeft(Seq.empty[(TextRange, MarkerType)], 0) {
      case ((ranges, removedChars), (range, markerType)) =>
        val (startMarker, endMarker) = startEndMarkers(markerType)

        val start = range.getStartOffset - removedChars
        val end = range.getEndOffset - removedChars - startMarker.length

        val rangesUpdated = ranges :+ (TextRange.create(start, end), markerType)
        val removedCharsUpdated = removedChars + startMarker.length + endMarker.length

        (rangesUpdated, removedCharsUpdated)
    }._1

    assertNonIntersecting(rangesFixed.map(_._1))

    // it is not most efficient way: we could build text by parts in the previous foldLeft and StringBuilder
    // but it a little clear this way and doesn't matter much in tests cause it anyway costs milliseconds
    val textFixed = startEndMarkers.foldLeft(inputText) { case (text, (startMarker, endMarker)) =>
      text.replace(startMarker, "").replace(endMarker, "")
    }

    (textFixed, rangesFixed)
  }

  private def assertNonIntersecting(ranges: Seq[TextRange]): Unit =
    ranges.sliding(2).foreach {
      case Seq(prev, curr) =>
        assertTrue("ranges shouldn't intersect", curr.getStartOffset >= prev.getEndOffset)
      case _ =>
    }
}

object MarkersUtils extends Markers