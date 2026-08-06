package org.jetbrains.sbt.process.options.parsing.model

/**
 * One raw sbt option input that could not be tokenized because its quoting is malformed.
 *
 * @param lineNumber    1-based line number within the original source
 * @param unclosedQuote the quote character that was opened but not closed
 * @param lineContent   raw malformed line content
 */
private[sbt] final case class MalformedSbtOption(
  lineNumber: Int,
  unclosedQuote: Char,
  lineContent: String = ""
)
