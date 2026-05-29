package org.jetbrains.sbt.process.options.collecting

import com.intellij.execution.configuration.EnvironmentVariablesData
import org.jetbrains.sbt.process.options.parsing.model.{ParsedSbtOption, SbtOptionsParseResult, SbtOptionsSource, UnrecognizedSbtOptions}
import org.jetbrains.sbt.process.options.parsing.{SbtOptionsParser, SbtOptionsTextNormalizer}

import java.nio.file.Path

/**
 * Collects and parses sbt options from `SBT_OPTS`, `.sbtopts`, and IDE settings
 *
 * @see [[SbtProcessOptionsResolver]] for the source order and pipeline
 *
 * Coverage:
 * - Indirectly covered by [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolverTest]] sbt-option source
 *   collection cases.
 */
private[options] object SbtOptionsCollector {

  /**
   * Collected sbt options partitioned into parsed entries and source-grouped diagnostics.
   *
   * @param parsed       inputs successfully mapped to [[ParsedSbtOption]]
   * @param unrecognised inputs that could not be recognised, grouped by their source
   */
  final case class CollectionResult(
    parsed: Seq[ParsedSbtOption],
    unrecognised: Seq[UnrecognizedSbtOptions]
  ) {
    def ++(other: CollectionResult): CollectionResult =
      CollectionResult(
        parsed ++ other.parsed,
        unrecognised ++ other.unrecognised
      )
  }

  object CollectionResult {
    val empty: CollectionResult = CollectionResult(Seq.empty, Seq.empty)

    def fromParseResult(result: SbtOptionsParseResult): CollectionResult =
      CollectionResult(result.parsed, result.unrecognised)
  }

  private val SbtOptsEnvVarName = "SBT_OPTS"
  private val SbtOptsFileName: String = ".sbtopts"

  def collect(
    sbtOptionsFromSettings: Seq[String],
    workingDir: Path,
    env: EnvironmentVariablesData
  ): CollectionResult = {
    val fromEnvVar = readFromEnvVar(env)
    val fromFile = readFromFileInDir(workingDir)
    val fromSettings = parse(sbtOptionsFromSettings, SbtOptionsSource.IdeSettings)

    fromEnvVar ++ fromFile ++ fromSettings
  }

  private def readFromEnvVar(env: EnvironmentVariablesData): CollectionResult = {
    val envVarValue = env.getEffectiveEnvironmentValue(SbtOptsEnvVarName)
    val parsed = envVarValue.map(parseFromEnvVar)
    parsed.getOrElse(CollectionResult.empty)
  }

  private def parseFromEnvVar(options: String): CollectionResult = {
    val normalizedOptions = SbtOptionsTextNormalizer.normalize(options)
    parse(normalizedOptions, SbtOptionsSource.EnvironmentVariable)
  }

  private def readFromFileInDir(directory: Path): CollectionResult = {
    val fileLines = readLinesIfReadable(directory, SbtOptsFileName)
    parseFromFileLines(fileLines)
  }

  private def parseFromFileLines(fileLines: Seq[String]): CollectionResult = {
    val normalizedOptions = fileLines.flatMap(SbtOptionsTextNormalizer.normalize).map(_.trim).filter(_.nonEmpty)
    parse(normalizedOptions, SbtOptionsSource.OptionsFile)
  }

  private def parse(options: Seq[String], source: SbtOptionsSource): CollectionResult =
    CollectionResult.fromParseResult(SbtOptionsParser.parse(options, source))
}
