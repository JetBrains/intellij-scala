package org.jetbrains.plugins.scala.projectHighlighting.reporter

private object IndentUtils {

  implicit class StringExt(private val str: String) extends AnyVal {
    def indented(spaces: Int): String = {
      val indentStr = " " * spaces
      indentStr + str.replace("\n", "\n" + indentStr)
    }
  }
}
