package org.jetbrains.sbt.process.options.collecting

import com.intellij.execution.configuration.EnvironmentVariablesData
import org.jetbrains.sbt.process.options.parsing.model.SbtOptionsDiagnostic
import org.jetbrains.sbt.process.options.parsing.model.SbtOptionsDiagnostic.{Malformed, Unrecognized}
import org.jetbrains.sbt.process.options.parsing.model.{MalformedSbtOption, ParsedSbtOption, SbtOptionsParseResult, SbtOptionsSource}
import org.jetbrains.sbt.process.options.parsing.{SbtOptionsParser, SbtOptionsTextNormalizer}

import java.nio.file.Path

/**
 * Collects and parses sbt options from `SBT_OPTS`, `.sbtopts`, and IDE settings
 *
 * @see [[SbtProcessOptionsResolver]] for the source order and pipeline
 *
 *      Coverage:
 * - Indirectly covered by [[org.jetbrains.sbt.process.options.SbtProcessOptionsResolverTest]] sbt-option source
 *   collection cases.
 */
private[options] object SbtOptionsCollector {

  /**
   * Collected sbt options partitioned into parsed entries and source-grouped diagnostics.
   *
   * @param parsed      inputs successfully mapped to [[ParsedSbtOption]]
   * @param diagnostics inputs that could not be recognized or tokenized, grouped by their source
   */
  final case class CollectionResult(
    parsed: Seq[ParsedSbtOption],
    diagnostics: Seq[SbtOptionsDiagnostic]
  ) {
    def ++(other: CollectionResult): CollectionResult =
      CollectionResult(
        parsed ++ other.parsed,
        diagnostics ++ other.diagnostics
      )
  }

  object CollectionResult {
    val empty: CollectionResult = CollectionResult(Seq.empty, Seq.empty)

    def fromParseResult(result: SbtOptionsParseResult): CollectionResult =
      CollectionResult(result.parsed, result.diagnostics)
  }

  private val SbtOptsEnvVarName = "SBT_OPTS"
  private val SbtOptsFileName: String = ".sbtopts"

  def collect(
    sbtOptionsFromSettings: Seq[String],
    workingDir: Path,
    env: EnvironmentVariablesData,
    malformedOptionsFromSettings: Seq[MalformedSbtOption] = Seq.empty
  ): CollectionResult = {
    val fromEnvVar = readFromEnvVar(env)
    val fromFile = readFromFileInDir(workingDir)
    val fromSettings =
      parse(sbtOptionsFromSettings, SbtOptionsSource.IdeSettings) ++
        collectMalformed(malformedOptionsFromSettings, SbtOptionsSource.IdeSettings)

    fromEnvVar ++ fromFile ++ fromSettings
  }

  private def readFromEnvVar(env: EnvironmentVariablesData): CollectionResult = {
    val envVarValue = env.getEffectiveEnvironmentValue(SbtOptsEnvVarName)
    val parsed = envVarValue.map(parseFromEnvVar)
    parsed.getOrElse(CollectionResult.empty)
  }

  private def parseFromEnvVar(options: String): CollectionResult = {
    val normalized = SbtOptionsTextNormalizer.normalize(options)
    parse(normalized.options, SbtOptionsSource.EnvironmentVariable) ++
      collectMalformed(normalized.malformedOptions, SbtOptionsSource.EnvironmentVariable)
  }

  private def readFromFileInDir(directory: Path): CollectionResult = {
    val file = directory.resolve(SbtOptsFileName)
    val fileLines = readLinesIfReadable(file)
    parseFromFileLines(fileLines, file)
  }

  private def parseFromFileLines(fileLines: Seq[String], file: Path): CollectionResult = {
    val normalizedLines = fileLines.zipWithIndex.map { case (line, index) =>
      val lineNumber = index + 1
      val normalized = SbtOptionsTextNormalizer.normalize(line)
      lineNumber -> normalized.copy(
        malformedOptions = normalized.malformedOptions.map(_.copy(lineNumber = lineNumber))
      )
    }

    val normalizedOptions = normalizedLines.flatMap { case (lineNumber, normalized) =>
      normalized.options.map(_.trim).filter(_.nonEmpty).map(_ -> lineNumber)
    }
    val malformedOptions = normalizedLines.flatMap { case (_, normalized) => normalized.malformedOptions }
    parseWithLineNumbers(normalizedOptions, SbtOptionsSource.OptionsFile, optionsFile = Some(file))
      ++ collectMalformed(malformedOptions, SbtOptionsSource.OptionsFile, optionsFile = Some(file))
  }

  private def parse(options: Seq[String], source: SbtOptionsSource, optionsFile: Option[Path] = None): CollectionResult = {
    val result = SbtOptionsParser.parse(options, source)
    fromParseResult(result, optionsFile)
  }

  private def parseWithLineNumbers(
    options: Seq[(String, Int)],
    source: SbtOptionsSource,
    optionsFile: Option[Path]
  ): CollectionResult = {
    val result = SbtOptionsParser.parseWithLineNumbers(options, source)
    fromParseResult(result, optionsFile)
  }

  private def fromParseResult(result: SbtOptionsParseResult, optionsFile: Option[Path]): CollectionResult =
    CollectionResult(
      result.parsed,
      result.diagnostics.map {
        case Unrecognized(source, unrecognizedOptions, _) => Unrecognized(source, unrecognizedOptions, optionsFile)
        case other => other
      }
    )

  private def collectMalformed(
    malformedOptions: Seq[MalformedSbtOption],
    source: SbtOptionsSource,
    optionsFile: Option[Path] = None,
  ): CollectionResult =
    if (malformedOptions.isEmpty)
      CollectionResult.empty
    else
      CollectionResult(Seq.empty, Seq(Malformed(source, malformedOptions, optionsFile)))
}
