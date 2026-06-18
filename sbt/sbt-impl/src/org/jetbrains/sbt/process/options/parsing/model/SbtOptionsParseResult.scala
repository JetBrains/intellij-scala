package org.jetbrains.sbt.process.options.parsing.model

/**
 * Parser-local outcome of mapping raw sbt option strings to recognised options and diagnostics.
 *
 * Coverage:
 * - Directly asserted in [[org.jetbrains.sbt.process.options.parsing.SbtOptionsParserTest]].
 * - Indirectly covered by [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolverTest]].
 */
private[options] final case class SbtOptionsParseResult(
  parsed: Seq[ParsedSbtOption],
  diagnostics: Seq[SbtOptionsDiagnostic]
)
