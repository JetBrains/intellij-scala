package org.jetbrains.plugins.scala.compiler.highlighting

private object CompilerMessages {
  def description(message: String): String =
    message.trim.stripSuffix(lineText(message))

  def isUnusedImport(description: String): Boolean =
    description.trim.equalsIgnoreCase("unused import")

  def isNoWarningsCanBeIncurred(description: String): Boolean =
    description.trim.equalsIgnoreCase("No warnings can be incurred under -Werror (or -Xfatal-warnings)")

  private def lineText(messageText: String): String = {
    val trimmed = messageText.trim
    val lastLineSeparator = trimmed.lastIndexOf('\n')
    if (lastLineSeparator > 0) trimmed.substring(lastLineSeparator) else ""
  }
}
