package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.TextRange
import org.junit.Assert._

import scala.annotation.tailrec
import scala.collection.mutable

trait Markers {

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
  def extractMarkers(inputText: String): (String, Seq[TextRange]) = {
    var input = inputText
    val markerPositions = mutable.Map[String, Int]()
    val textRanges = mutable.Buffer.empty[TextRange]

    def detectMarker(start: String, end: String): Boolean = {
      input.contains(start) && input.contains(end) && {
        markerPositions.put(start, input.indexOf(start))
        markerPositions.put(end, input.indexOf(end))
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

    input -> textRanges
  }

  def extractSequentialMarkers(inputText: String): (String, Seq[TextRange]) =
    MarkersUtils.extractSequentialMarkers(inputText, startMarker, endMarker)

  private def sortMarkers(sorted: List[(String, Int)], offset: Int = 0): List[(String, Int)] = {
    sorted match {
      case (marker, position) :: tail => (marker, position - offset) :: sortMarkers(tail, offset + marker.length)
      case _ => sorted
    }
  }
}


object MarkersUtils {

  /**
   * Used to extract ranges that do not do not intersect
   *
   * @example
   * line /start/ 1 content /end/
   * line /start/ 2 /end/ /start/ content /end/
   */
  def extractSequentialMarkers(inputText: String, startMarker: String, endMarker: String): (String, Seq[TextRange]) = {
    val startReg = s"\\Q$startMarker\\E".r
    val endReg = s"\\Q$endMarker\\E".r

    val startIndexes = startReg.findAllMatchIn(inputText).map(_.start).toList
    val endIndexes = endReg.findAllMatchIn(inputText).map(_.start).toList
    assertEquals(
      s"start & end markers counts are not equal\nstart: $startIndexes,\nend: $endIndexes",
      startIndexes.size,
      endIndexes.size
    )
    val ranges = startIndexes.zip(endIndexes).map { case (s, e) => TextRange.create(s, e)}
    ranges.foreach { range =>
      assertTrue("range end offset can't be smaller then start offset", range.getEndOffset >= range.getStartOffset)
    }
    ranges.sliding(2).foreach {
      case Seq(prev, curr) => assertTrue("ranges shouldn't intersect", curr.getStartOffset >= prev.getEndOffset)
      case _ =>
    }

    val rangesFixed = ranges.zipWithIndex.map { case (range, idx) =>
      val alreadyRemovedChars = idx * (endMarker.length + startMarker.length)
      val start = range.getStartOffset - alreadyRemovedChars
      val end = range.getEndOffset - alreadyRemovedChars - startMarker.length // plus first start marker
      TextRange.create(start, end)
    }

    val textFixed = inputText.replace(startMarker, "").replace(endMarker, "")

    (textFixed, rangesFixed)
  }
}