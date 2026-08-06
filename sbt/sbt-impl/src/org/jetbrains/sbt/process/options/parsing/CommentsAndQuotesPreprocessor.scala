package org.jetbrains.sbt.process.options.parsing

import org.jetbrains.sbt.process.options.parsing.model.MalformedSbtOption

/**
 * Removes comments while preserving quoted content and rejects input with unbalanced quotes.
 */
private[options] object CommentsAndQuotesPreprocessor {

  final case class PreprocessResult(
    preprocessedText: Option[String],
    malformedOptions: Seq[MalformedSbtOption]
  )

  def preprocess(text: String): PreprocessResult = {
    val lineResults = text
      .split("\\R", -1)
      .toSeq
      .zipWithIndex
      .map { case (line, index) => preprocessLine(line, index + 1) }

    val malformedOptions = lineResults.collect { case LinePreprocessResult.Malformed(option) => option }
    val preprocessedText = Option.when(malformedOptions.isEmpty) {
      lineResults.collect { case LinePreprocessResult.Preprocessed(line) => line }.mkString("\n")
    }

    PreprocessResult(preprocessedText, malformedOptions)
  }

  private def isQuote(char: Char): Boolean =
    char == '"' || char == '\''

  private enum LinePreprocessResult {
    case Preprocessed(line: String)
    case Malformed(option: MalformedSbtOption)
  }

  private def preprocessLine(line: String, lineNumber: Int): LinePreprocessResult = {
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
        return LinePreprocessResult.Preprocessed(line.substring(0, index))

      index += 1
    }

    activeQuote match {
      case Some(unclosedQuote) =>
        LinePreprocessResult.Malformed(MalformedSbtOption(lineNumber, unclosedQuote, line))
      case None =>
        LinePreprocessResult.Preprocessed(line)
    }
  }
}
