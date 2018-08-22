package org.jetbrains.plugins.scala.lang.formatter.tests

import java.util

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

import scala.annotation.tailrec
import scala.collection.mutable

trait SelectionTest extends AbstractScalaFormatterTestBase {

  def startMarker(i: Int) = s"/*start$i*/"
  def endMarker(i: Int) = s"/*end$i*/"
  val startMarker = "/*start*/"
  val endMarker = "/*end*/"

  override def doTextTest(text: String, textAfter: String): Unit = {
    myTextRanges = new util.LinkedList[TextRange]()
    var input = StringUtil.convertLineSeparators(text)
    val markerPositions = mutable.Map[String, Int]()

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

    detectMarker(startMarker, endMarker)
    val index = detectNumberedMarkers(0)
    val adjustedMarkers = sortMarkers(markerPositions.toList.sortBy(_._2)).toMap

    def addRange(start: String, end: String): Boolean = {
        input = input.replace(start, "")
        input = input.replace(end, "")
        myTextRanges.add(new TextRange(adjustedMarkers(start), adjustedMarkers(end)))
    }

    addRange(startMarker, endMarker)
    for (i <- 0 until index) { addRange(startMarker(i), endMarker(i)) }

    super.doTextTest(input, StringUtil.convertLineSeparators(textAfter))
  }

  private def sortMarkers(sorted: List[(String, Int)], offset: Int = 0): List[(String, Int)] = {
    sorted match {
      case (marker, position) :: tail => (marker, position - offset) :: sortMarkers(tail, offset + marker.length)
      case _ => sorted
    }
  }

}
