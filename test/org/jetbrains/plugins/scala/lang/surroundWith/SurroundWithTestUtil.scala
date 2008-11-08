package org.jetbrains.plugins.scala.lang.surroundWith

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.11.2008
 */

object SurroundWithTestUtil {
  val startMarker = "<start>"
  val endMarkers: Array[String] = Array[String]("<if>",
    "<else>", "<while>", "<do>", "<for>", "<yield>"
    , "<catch>", "<finally>", "<try>", "<braces>",
    "<match>", "<parenthesis>", "<if_cond>", "<else_cond>", "<unary>")
  def prepareFile(text: String): (String, Int, Int, Int) = {
    var workingText = text
    val start = text.indexOf(startMarker)
    val t = endMarkers.findIndexOf(text.indexOf(_) != -1)
    val s: String = endMarkers(t)
    val end = text.indexOf(s) - startMarker.length
    workingText = removeMarker(workingText, startMarker)
    workingText = removeMarker(workingText, s)
    (workingText, start, end, t)
  }

  private def removeMarker(text: String, marker: String): String = {
    val i = text.indexOf(marker)
    text.substring(0, i) + text.substring(i + marker.length)
  }
}