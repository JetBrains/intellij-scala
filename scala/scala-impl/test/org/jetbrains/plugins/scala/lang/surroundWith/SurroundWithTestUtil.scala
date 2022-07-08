package org.jetbrains.plugins.scala
package lang
package surroundWith

object SurroundWithTestUtil {
  val startMarker = "<start>"
  val endMarkers: Array[String] = Array[String]("<if>",
    "<else>", "<while>", "<do>", "<for>", "<yield>"
    , "<catch>", "<finally>", "<try>", "<braces>",
    "<match>", "<parenthesis>", "<if_cond>", "<else_cond>", "<unary>")
  def prepareFile(text: String): (String, Integer, Integer, Integer) = {
    var workingText = text
    val start = text.indexOf(startMarker)
    val t = endMarkers.indexWhere(text.indexOf(_) != -1)
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