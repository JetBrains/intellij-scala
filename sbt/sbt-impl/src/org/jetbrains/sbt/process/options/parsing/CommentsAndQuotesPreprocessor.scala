package org.jetbrains.sbt.process.options.parsing

/**
 * Removes comments while preserving quoted content and rejects input with unbalanced quotes.
 */
private[options] object CommentsAndQuotesPreprocessor {

  /**
   * Removes comments from text and returns `None` when the remaining input has unbalanced quotes.<br>
   *
   * Everything after `#` is discarded, unless that `#` appears inside quotes.
   *
   * @param text input text to preprocess
   * @return `Some(preprocessedText)` when comments are removed and quotes are balanced;<br>
   *         `None` when the input contains unbalanced single or double quotes
   * @example {{{
   *   // Input:
   *   """ command "value # stays" # comment """
   *   // Output
   *   Some(""" command "value # stays" """)
   * }}}
   *
   * @example {{{
   *   // Input:
   *   """ command "unterminated quote """
   *   // Output:
   *   None
   * }}}
   */
  def preprocess(text: String): Option[String] = {
    val lines = text.split("\\R", -1)
    val preprocessedLines = lines.foldLeft(Option(Vector.empty[String])) {
      case (Some(lines), line) =>
        preprocessLine(line).map(lines :+ _)
      case (None, _) =>
        None
    }
    preprocessedLines.map(_.mkString("\n"))
  }

  private def isQuote(char: Char): Boolean =
    char == '"' || char == '\''

  private def preprocessLine(line: String): Option[String] = {
    var activeQuote = Option.empty[Char]
    var index = 0

    while (index < line.length) {
      val char = line.charAt(index)

      activeQuote match {
        case Some(quote) if char == quote =>
          activeQuote = None
        case None if isQuote(char) =>
          activeQuote = Some(char)
        case _ =>
      }

      if (char == '#' && activeQuote.isEmpty)
        return Some(line.substring(0, index))

      index += 1
    }

    if (activeQuote.isEmpty)
      Some(line)
    else
      None
  }
}
