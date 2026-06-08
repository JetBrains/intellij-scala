package org.jetbrains.sbt.process.options.parsing

import com.intellij.util.text.EditDistance
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOption.Form.{InlineValue, NoValue, SeparateValue}
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOption.Spelling
import org.jetbrains.sbt.process.options.knownOptions.{KnownSbtOption, KnownSbtOptions}
import org.jetbrains.sbt.process.options.parsing.model.SbtOptionsDiagnostic.Unrecognized
import org.jetbrains.sbt.process.options.parsing.model.{ParsedSbtOption, SbtOptionsParseResult, SbtOptionsSource, UnrecognizedSbtOption}

import scala.collection.mutable.ListBuffer

/**
 * Parser entry point for normalized sbt option entries
 *
 * @see [[SbtProcessOptionsResolver]] for the surrounding pipeline
 */
private[options] object SbtOptionsParser {

  def parse(
    options: Seq[String],
    source: SbtOptionsSource
  ): SbtOptionsParseResult =
    parseWithLineNumbers(options.map(_ -> 1), source)

  def parseWithLineNumbers(
    options: Seq[(String, Int)],
    source: SbtOptionsSource
  ): SbtOptionsParseResult =
    new SbtOptionsParser(source).parseWithLineNumbers(options)
}

/**
 * Maps normalized sbt option entries to recognized option occurrences and grouped diagnostics.
 */
private[options] final class SbtOptionsParser(source: SbtOptionsSource) {

  /**
   * @param rawOptions raw options collected from sources such as `.sbtopts`, `SBT_OPTS`, or IDE settings
   */
  def parse(rawOptions: Seq[String]): SbtOptionsParseResult =
    parseWithLineNumbers(rawOptions.map(_ -> 1))

  /**
   * @param rawOptions raw options with their 1-based source lines
   */
  def parseWithLineNumbers(rawOptions: Seq[(String, Int)]): SbtOptionsParseResult = {
    val mappedOptions = ListBuffer[ParsedSbtOption]()
    val unrecognizedOptions = ListBuffer[UnrecognizedSbtOption]()

    rawOptions.foreach { case (opt, lineNumber) =>
      val res = mapOptionToSbtOption(opt)
      res match {
        case Some(value) =>
          mappedOptions += value
        case None =>
          unrecognizedOptions += UnrecognizedSbtOption(opt, findClosestOptionHelper(opt), lineNumber)
      }
    }

    val diagnostics = Option.when(unrecognizedOptions.nonEmpty) {
      Unrecognized(source, unrecognizedOptions.toSeq)
    }.toSeq

    SbtOptionsParseResult(mappedOptions.toSeq, diagnostics)
  }

  private def mapOptionToSbtOption(opt: String): Option[ParsedSbtOption] = {
    if (opt.startsWith("-J"))
      Some(ParsedSbtOption.RawJvmSbtOption(opt.substring(2)))
    else if (opt.startsWith("-D"))
      Some(ParsedSbtOption.RawJvmSbtOption(opt))
    else
      mapToKnownSbtOption(opt)
  }

  private def mapToKnownSbtOption(option: String): Option[ParsedSbtOption] =
    KnownSbtOptions
      .findMatchingSpelling(option)
      .flatMap { case (entry, spelling) => mapKnownOption(option, entry, spelling) }

  private def mapKnownOption(
    rawOption: String,
    entry: KnownSbtOption,
    spelling: Spelling
  ): Option[ParsedSbtOption] = {
    val optionValue = rawOption.stripPrefix(spelling.text)
    spelling.valueForm match {
      case NoValue =>
        Option.when(optionValue.isEmpty) {
          ParsedSbtOption.DefinedSbtOption(entry, None)
        }
      case SeparateValue =>
        mapValueBearingOption(optionValue, optionValue.matches("^\\s+.*"), entry)
      case InlineValue =>
        mapValueBearingOption(optionValue, optionValue.matches("^[^\\s]+.*"), entry)
    }
  }

  private def mapValueBearingOption(
    optionValue: String,
    hasValidValueSyntax: Boolean,
    entry: KnownSbtOption
  ): Option[ParsedSbtOption] =
    Option.when(optionValue.trim.nonEmpty && hasValidValueSyntax) {
      ParsedSbtOption.DefinedSbtOption(entry, Some(optionValue.trim))
    }

  private def findClosestOptionHelper(userOpt: String): Option[String] = {
    val closestOption = KnownSbtOptions.AllSpellings
      .map { case (optionKey, (_, spelling)) =>
        val truncatedOptFromArg: String =
          spelling.valueForm match {
            case SeparateValue => userOpt.split(' ')(0)
            case InlineValue => userOpt.split("=")(0)
            case NoValue => userOpt
          }
        val distance = EditDistance.optimalAlignment(optionKey, truncatedOptFromArg.trim, false, 2)
        optionKey -> distance
      }
      .filter(_._2 <= 2)
      .minByOption(_._2)
    closestOption.map { case (optionKey, _) => KnownSbtOptions.AllSpellings(optionKey)._2.helperMsg }
  }
}
