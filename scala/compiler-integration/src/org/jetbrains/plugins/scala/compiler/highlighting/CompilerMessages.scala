package org.jetbrains.plugins.scala.compiler.highlighting

import scala.util.matching.Regex

private object CompilerMessages {
  private val bspMessageTemplate: Regex = """^(.*)(\s\[\d+:\d+\])$""".r

  def description(message: String): String = {
    val trimmedMessage = message.trim
    val last = lastLine(trimmedMessage)
    val suffix = last match {
      case bspMessageTemplate(_, position) => position
      case line => line
    }
    trimmedMessage.stripSuffix(suffix).trim
  }

  def isUnusedImport(description: String): Boolean =
    description.trim.equalsIgnoreCase("unused import")

  def isNoWarningsCanBeIncurred(description: String): Boolean =
    description.trim.equalsIgnoreCase("No warnings can be incurred under -Werror (or -Xfatal-warnings)")

  private def lastLine(text: String): String = {
    val lastLineSeparator = text.lastIndexOf('\n')
    if (lastLineSeparator != -1)
      text.substring(lastLineSeparator).trim
    else
      text
  }
}
