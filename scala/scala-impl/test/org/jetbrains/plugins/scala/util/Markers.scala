package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.TextRange

import scala.annotation.tailrec
import scala.collection.mutable

trait Markers {
  def startMarker(i: Int) = s"/*start$i*/"
  def endMarker(i: Int) = s"/*end$i*/"
  val startMarker = "/*start*/"
  val endMarker = "/*end*/"

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

  private def sortMarkers(sorted: List[(String, Int)], offset: Int = 0): List[(String, Int)] = {
    sorted match {
      case (marker, position) :: tail => (marker, position - offset) :: sortMarkers(tail, offset + marker.length)
      case _ => sorted
    }
  }
}
