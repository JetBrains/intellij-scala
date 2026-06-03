package org.jetbrains.plugins.scala.lang.surroundWith

object SurroundWithTestUtil {
  // could be handy to keep trailing whitespaces at the end of the line
  private val preserveTrailingSpacesMarker = "<preserve-trailing-spaces>"

  private val startMarker = "<start>"
  private val endMarkers: Array[String] = Array[String]("<if>",
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

  def prepareExpectedResult(text: String): String =
    text.replace(preserveTrailingSpacesMarker, "")

  private def removeMarker(text: String, marker: String): String = {
    val i = text.indexOf(marker)
    text.substring(0, i) + text.substring(i + marker.length)
  }
}
