package org.jetbrains.sbt.process.options.parsing

import org.jetbrains.sbt.process.options.parsing.SbtOptionsTextNormalizer.NormalizationResult
import org.jetbrains.sbt.process.options.parsing.model.MalformedSbtOption
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit coverage for [[SbtOptionsTextNormalizer]].
 *
 * ## Test coverage
 * Primary coverage:
 * - [[SbtOptionsTextNormalizer]]
 *
 * Indirect coverage:
 * - [[CommentsAndQuotesPreprocessor]]
 * - [[org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptions]]
 */
class SbtOptionsTextNormalizerTest {

  private def assertNormalized(providedOpts: String, expected: NormalizationResult): Unit = {
    val actual = SbtOptionsTextNormalizer.normalize(providedOpts)
    assertEquals(expected, actual)
  }

  private def result(options: Seq[String], malformedOptions: Seq[MalformedSbtOption] = Seq.empty): NormalizationResult =
    NormalizationResult(options, malformedOptions)

  @Test
  def combinesOptionWithQuotedArgument(): Unit =
    assertNormalized(
      """ -sbt-dir "temp dir" -color=always -d dummy """,
      result(Seq("-sbt-dir temp dir", "-color=always", "-d", "dummy"))
    )

  @Test
  def normalizesLongOptionsAndPreservesShortDoubleDashOption(): Unit =
    assertNormalized(
      """ --sbt-dir "temp di'r" -color=always  --d dummy """,
      result(Seq("-sbt-dir temp di'r", "-color=always", "--d", "dummy"))
    )

  @Test
  def returnsEmptyForUnbalancedQuotes(): Unit =
    assertNormalized(
      """ --sbt-dir "temp dir -color=always  --d dummy """,
      result(Seq.empty, Seq(MalformedSbtOption(lineNumber = 1, unclosedQuote = '"', lineContent = """ --sbt-dir "temp dir -color=always  --d dummy """)))
    )

  @Test
  def reportsUnbalancedQuotes(): Unit =
    assertNormalized(
      """ --sbt-dir "temp dir -color=always  --d dummy """,
      result(Seq.empty, Seq(MalformedSbtOption(lineNumber = 1, unclosedQuote = '"', lineContent = """ --sbt-dir "temp dir -color=always  --d dummy """)))
    )

  @Test
  def keepsOptionWithoutFollowingArgumentUnchanged(): Unit =
    assertNormalized(
      """-d -sbt-dir""",
      result(Seq("-d", "-sbt-dir"))
    )

  @Test
  def doesNotCombineOptionWithEmptyQuotedArgument(): Unit =
    assertNormalized(
      """ -d -sbt-dir "" """,
      result(Seq("-d", "-sbt-dir", ""))
    )

  @Test
  def doesNotCombineOptionWithBlankQuotedArgument(): Unit =
    assertNormalized(
      """ -d -sbt-dir "   " """,
      //Q: is it a correct thing to expect indeed?
      result(Seq("-d", "-sbt-dir    "))
    )

  @Test
  def combinesOptionWithQuotedArgumentContainingSpaces(): Unit =
    assertNormalized(
      """ -d -sbt-dir "/tmp/sbt dir" """,
      result(Seq("-d", "-sbt-dir /tmp/sbt dir"))
    )

  @Test
  def combinesOptionWithQuotedArgumentContainingSpaces_LeadingAndTrailing(): Unit =
    assertNormalized(
      """ -d -sbt-dir "  /tmp/sbt dir  " """,
      //Q: is it a correct thing to expect indeed?
      result(Seq("-d", "-sbt-dir   /tmp/sbt dir  "))
    )


  @Test
  def doesNotCombineOptionWithNextOptionAsArgument(): Unit =
    assertNormalized(
      """-d -sbt-dir -dummy""",
      result(Seq("-d", "-sbt-dir", "-dummy"))
    )

  @Test
  def combinesMultipleOptionArgumentPairs(): Unit =
    assertNormalized(
      """-sbt-dir /tmp/sbt -ivy /tmp/ivy -jvm-debug 5005""",
      result(Seq("-sbt-dir /tmp/sbt", "-ivy /tmp/ivy", "-jvm-debug 5005"))
    )

  @Test
  def keepsFlagOnlyJvmOptionsUnchanged(): Unit =
    assertNormalized(
      """-no-global -no-share -debug-inc -traces -timings -no-colors""",
      result(Seq("-no-global", "-no-share", "-debug-inc", "-traces", "-timings", "-no-colors"))
    )

  @Test
  def doesNotCombineOptionThatAlreadyContainsItsValue(): Unit =
    assertNormalized(
      """-color=always dummy""",
      result(Seq("-color=always", "dummy"))
    )

  @Test
  def doesNotCombineOptionThatEndsWithEquals(): Unit =
    assertNormalized(
      """-color= always""",
      result(Seq("-color=", "always"))
    )

  @Test
  def normalizesStandaloneLongLauncherOptions(): Unit =
    assertNormalized(
      """--debug --warn --info --error""",
      result(Seq("-debug", "-warn", "-info", "-error"))
    )

  @Test
  def normalizesUnknownLongOptionWithoutCombiningIt(): Unit =
    assertNormalized(
      """--unknown value""",
      result(Seq("-unknown", "value"))
    )

  @Test
  def returnsEmptyForEmptyInput(): Unit =
    assertNormalized(
      "",
      result(Seq.empty)
    )

  @Test
  def returnsEmptyForCommentOnlyInput(): Unit =
    assertNormalized(
      """# comment only""",
      result(Seq.empty)
    )

  private def assertIsolatedOption(providedOpt: String, expectedOpt: String = null): Unit =
    assertNormalized(providedOpt, result(Seq(Option(expectedOpt).getOrElse(providedOpt))))

  @Test
  def keepsIsolatedKnownShortLauncherOptionUnchanged(): Unit =
    assertIsolatedOption("-d")

  @Test
  def normalizesIsolatedKnownLongLauncherDebugOption(): Unit =
    assertIsolatedOption("--debug", "-debug")

  @Test
  def keepsIsolatedKnownValueTakingShortSbtBootOptionUnchanged(): Unit =
    assertIsolatedOption("-sbt-boot")

  @Test
  def normalizesIsolatedKnownValueTakingLongSbtBootOption(): Unit =
    assertIsolatedOption("--sbt-boot", "-sbt-boot")

  @Test
  def keepsIsolatedKnownValueTakingShortSbtDirOptionUnchanged(): Unit =
    assertIsolatedOption("-sbt-dir")

  @Test
  def normalizesIsolatedKnownValueTakingLongSbtDirOption(): Unit =
    assertIsolatedOption("--sbt-dir", "-sbt-dir")

  @Test
  def normalizesIsolatedKnownInlineValueTakingLongColorOption(): Unit =
    assertIsolatedOption("--color=", "-color=")

  // Line-separator handling

  @Test
  def parsesOptionsOnSeparateLinesWithUnixLineEndings(): Unit =
    assertNormalized(
      "-sbt-dir /tmp\n-debug\n-color=always",
      result(Seq("-sbt-dir /tmp", "-debug", "-color=always"))
    )

  @Test
  def parsesOptionsOnSeparateLinesWithWindowsLineEndings(): Unit =
    assertNormalized(
      "-sbt-dir /tmp\r\n-debug\r\n-color=always",
      result(Seq("-sbt-dir /tmp", "-debug", "-color=always"))
    )

  @Test
  def parsesOptionsOnSeparateLinesWithCarriageReturnOnly(): Unit =
    assertNormalized(
      "-sbt-dir /tmp\r-debug\r-color=always",
      result(Seq("-sbt-dir /tmp", "-debug", "-color=always"))
    )

  @Test
  def parsesMultiLineInputWithBlankLines(): Unit =
    assertNormalized(
      "\n-debug\n\n-color=always\n",
      result(Seq("-debug", "-color=always"))
    )

  @Test
  def commentOnFirstLineKeepsFollowingLines(): Unit =
    assertNormalized(
      "-debug # comment\n-color=always\n-sbt-dir /tmp",
      result(Seq("-debug", "-color=always", "-sbt-dir /tmp"))
    )

  @Test
  def commentLineAtStartKeepsFollowingLines(): Unit =
    assertNormalized(
      "# comment only\n-debug\n-color=always",
      result(Seq("-debug", "-color=always"))
    )
}
