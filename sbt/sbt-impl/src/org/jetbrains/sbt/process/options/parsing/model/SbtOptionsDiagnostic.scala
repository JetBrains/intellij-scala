package org.jetbrains.sbt.process.options.parsing.model

import java.nio.file.Path

/**
 * Source-grouped diagnostics found while collecting and parsing sbt option inputs.
 */
private[options] sealed trait SbtOptionsDiagnostic {
  def source: SbtOptionsSource
}

private[options] object SbtOptionsDiagnostic {
  /**
   * Raw inputs from a single source that could not be recognized.
   *
   * @param source              where these inputs were collected from
   * @param unrecognizedOptions the individual inputs that could not be recognized
   * @param optionsFile         concrete file path when the source is an sbt options file
   */
  final case class Unrecognized(
    source: SbtOptionsSource,
    unrecognizedOptions: Seq[UnrecognizedSbtOption],
    optionsFile: Option[Path] = None,
  ) extends SbtOptionsDiagnostic

  /**
   * Malformed inputs from a single source.
   *
   * @param source           where these inputs were collected from
   * @param malformedOptions individual malformed inputs
   * @param optionsFile      concrete file path when the source is an sbt options file
   */
  final case class Malformed(
    source: SbtOptionsSource,
    malformedOptions: Seq[MalformedSbtOption],
    optionsFile: Option[Path] = None,
  ) extends SbtOptionsDiagnostic
}
