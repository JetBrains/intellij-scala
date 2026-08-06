package org.jetbrains.sbt.process.options.parsing.model

import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOption

/**
 * Parser model for one recognized sbt option occurrence; rendering is described by
 * [[org.jetbrains.sbt.process.options.SbtProcessOptionsRenderer]].
 *
 * Coverage:
 * - Directly asserted in [[org.jetbrains.sbt.process.options.parsing.SbtOptionsParserTest]].
 * - Indirectly covered by [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolverTest]] rendering assertions.
 */
private[options] sealed trait ParsedSbtOption

/**
 * Concrete parser result variants for recognized sbt options.
 */
private[options] object ParsedSbtOption {
  /**
   * A recognised sbt launcher option matched to a known [[KnownSbtOption]].
   *
   * @param entry       metadata for the matched sbt option
   * @param parsedValue value provided with the option, or `None` for flags such as `-debug`
   */
  final case class DefinedSbtOption(
    entry: KnownSbtOption,
    parsedValue: Option[String]
  ) extends ParsedSbtOption

  /**
   * A JVM option accepted in sbt option sources and passed to the JVM without sbt launcher mapping.
   *
   * @param value JVM option text to pass to the JVM
   */
  final case class RawJvmSbtOption(value: String) extends ParsedSbtOption
}
