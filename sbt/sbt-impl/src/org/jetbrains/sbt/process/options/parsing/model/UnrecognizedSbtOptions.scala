package org.jetbrains.sbt.process.options.parsing.model

/**
 * One raw input that could not be recognized as a supported sbt option.
 *
 * @param rawOption       the raw, unrecognized input as provided by the user/source
 * @param suggestedHelper closest known option's help text, when an input is a likely typo of a supported option
 */
private[options] final case class UnrecognizedSbtOption(rawOption: String, suggestedHelper: Option[String])

/**
 * Raw inputs from a single source that could not be recognized; source grouping is explained by
 * [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolver]].
 *
 * Coverage:
 * - Directly asserted in [[org.jetbrains.sbt.process.options.parsing.SbtOptionsParserTest]] and
 *   [[org.jetbrains.sbt.process.options.UnrecognizedSbtOptionsReporterTest]].
 * - Indirectly covered by [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolverTest]] warning assertions.
 *
 * @param source              where these inputs were collected from
 * @param unrecognizedOptions the individual inputs that could not be recognized
 */
private[options] final case class UnrecognizedSbtOptions(
  source: SbtOptionsSource,
  unrecognizedOptions: Seq[UnrecognizedSbtOption]
)
