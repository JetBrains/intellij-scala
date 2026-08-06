package org.jetbrains.sbt.process.options.parsing.model

/**
 * One raw input that could not be recognized as a supported sbt option.
 *
 * @param rawOption       the raw, unrecognized input as provided by the user/source
 * @param suggestedHelper closest known option's help text, when an input is a likely typo of a supported option
 * @param lineNumber      1-based source line where the input was found
 */
private[options] final case class UnrecognizedSbtOption(
  rawOption: String,
  suggestedHelper: Option[String],
  lineNumber: Int = 1
)
