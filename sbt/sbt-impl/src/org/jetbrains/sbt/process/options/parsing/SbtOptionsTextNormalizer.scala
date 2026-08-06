package org.jetbrains.sbt.process.options.parsing

import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOption.Form.SeparateValue
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptions
import org.jetbrains.sbt.process.options.parsing.model.MalformedSbtOption

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

/**
 * Converts raw sbt option text into logical option entries for [[SbtOptionsParser]]
 *
 * @see [[SbtProcessOptionsResolver]] for the surrounding pipeline
 */
private[options] object SbtOptionsTextNormalizer {

  final case class NormalizationResult(
    options: Seq[String],
    malformedOptions: Seq[MalformedSbtOption]
  )

  /**
   * Normalizes sbt option text into entries shaped for [[SbtOptionsParser.parse]].
   *
   * @param optionsText sbt option text
   * @return normalized logical option entries and malformed input diagnostics
   */
  def normalize(optionsText: String): NormalizationResult = {
    val preprocessed = CommentsAndQuotesPreprocessor.preprocess(optionsText)
    val optionsParsed = preprocessed.preprocessedText.map { options =>
      val optsParsed: Seq[String] = parseOptions(options)
      val optsWithoutDoubleDash = optsParsed.map(removeDoubleDashFromLongOptions)
      prependArgsToOpts(optsWithoutDoubleDash, Nil)
    }
    NormalizationResult(optionsParsed.getOrElse(Seq.empty), preprocessed.malformedOptions)
  }

  private def parseOptions(options: String): Seq[String] =
    ParametersListUtil.parse(options, false, true).asScala.toSeq

  @tailrec
  private def prependArgsToOpts(remaining: Seq[String], acc: List[String] = Nil): Seq[String] =
    remaining match {
      case opt +: value +: tail if shouldCombine(opt, value) =>
        val optionWithValue = s"$opt $value"
        prependArgsToOpts(tail, optionWithValue :: acc)

      case opt +: tail =>
        prependArgsToOpts(tail, opt :: acc)

      case _ =>
        acc.reverse
    }

  private def shouldCombine(option: String, tokenAfterOption: String): Boolean =
    expectsSeparateValue(option) &&
      isStandaloneValue(tokenAfterOption)

  private def expectsSeparateValue(optStr: String): Boolean = {
    val sbtOption = KnownSbtOptions.findExactSpelling(optStr)
    sbtOption.exists { case (_, spelling) => spelling.valueForm == SeparateValue }
  }

  private def isStandaloneValue(token: String): Boolean =
    token.nonEmpty && !token.startsWith("-")

  private val SingleCharacterOptionPattern: Regex = "-+.$".r

  private def isSingleCharOption(opt: String): Boolean =
    SingleCharacterOptionPattern.matches(opt)

  private def removeDoubleDashFromLongOptions(opt: String): String = {
    val removeSingleDash = opt.startsWith("--") && !isSingleCharOption(opt)
    if (removeSingleDash)
      opt.stripPrefix("-")
    else
      opt
  }
}
