package org.jetbrains.sbt.process.options.parsing

import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
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

  private def assertNormalizedOptions(providedOpts: String, expected: Seq[String]): Unit = {
    val actual = SbtOptionsTextNormalizer.normalize(providedOpts)
    assertCollectionEquals(expected, actual)
  }

  @Test
  def combinesOptionWithQuotedArgument(): Unit =
    assertNormalizedOptions(
      """ -sbt-dir "temp dir" -color=always -d dummy """,
      Seq("-sbt-dir temp dir", "-color=always", "-d", "dummy")
    )

  @Test
  def normalizesLongOptionsAndPreservesShortDoubleDashOption(): Unit =
    assertNormalizedOptions(
      """ --sbt-dir "temp di'r" -color=always  --d dummy """,
      Seq("-sbt-dir temp di'r", "-color=always", "--d", "dummy")
    )

  @Test
  def returnsEmptyForUnbalancedQuotes(): Unit =
    assertNormalizedOptions(
      """ --sbt-dir "temp dir -color=always  --d dummy """,
      Seq.empty
    )

  @Test
  def keepsOptionWithoutFollowingArgumentUnchanged(): Unit =
    assertNormalizedOptions(
      """-d -sbt-dir""",
      Seq("-d", "-sbt-dir")
    )

  @Test
  def doesNotCombineOptionWithEmptyQuotedArgument(): Unit =
    assertNormalizedOptions(
      """ -d -sbt-dir "" """,
      Seq("-d", "-sbt-dir", "")
    )

  @Test
  def doesNotCombineOptionWithBlankQuotedArgument(): Unit =
    assertNormalizedOptions(
      """ -d -sbt-dir "   " """,
      //Q: is it a correct thing to expect indeed?
      Seq("-d", "-sbt-dir    ")
    )

  @Test
  def combinesOptionWithQuotedArgumentContainingSpaces(): Unit =
    assertNormalizedOptions(
      """ -d -sbt-dir "/tmp/sbt dir" """,
      Seq("-d", "-sbt-dir /tmp/sbt dir")
    )

  @Test
  def combinesOptionWithQuotedArgumentContainingSpaces_LeadingAndTrailing(): Unit =
    assertNormalizedOptions(
      """ -d -sbt-dir "  /tmp/sbt dir  " """,
      //Q: is it a correct thing to expect indeed?
      Seq("-d", "-sbt-dir   /tmp/sbt dir  ")
    )


  @Test
  def doesNotCombineOptionWithNextOptionAsArgument(): Unit =
    assertNormalizedOptions(
      """-d -sbt-dir -dummy""",
      Seq("-d", "-sbt-dir", "-dummy")
    )

  @Test
  def combinesMultipleOptionArgumentPairs(): Unit =
    assertNormalizedOptions(
      """-sbt-dir /tmp/sbt -ivy /tmp/ivy -jvm-debug 5005""",
      Seq("-sbt-dir /tmp/sbt", "-ivy /tmp/ivy", "-jvm-debug 5005")
    )

  @Test
  def keepsFlagOnlyJvmOptionsUnchanged(): Unit =
    assertNormalizedOptions(
      """-no-global -no-share -debug-inc -traces -timings -no-colors""",
      Seq("-no-global", "-no-share", "-debug-inc", "-traces", "-timings", "-no-colors")
    )

  @Test
  def doesNotCombineOptionThatAlreadyContainsItsValue(): Unit =
    assertNormalizedOptions(
      """-color=always dummy""",
      Seq("-color=always", "dummy")
    )

  @Test
  def doesNotCombineOptionThatEndsWithEquals(): Unit =
    assertNormalizedOptions(
      """-color= always""",
      Seq("-color=", "always")
    )

  @Test
  def normalizesStandaloneLongLauncherOptions(): Unit =
    assertNormalizedOptions(
      """--debug --warn --info --error""",
      Seq("-debug", "-warn", "-info", "-error")
    )

  @Test
  def normalizesUnknownLongOptionWithoutCombiningIt(): Unit =
    assertNormalizedOptions(
      """--unknown value""",
      Seq("-unknown", "value")
    )

  @Test
  def returnsEmptyForEmptyInput(): Unit =
    assertNormalizedOptions(
      "",
      Seq.empty
    )

  @Test
  def returnsEmptyForCommentOnlyInput(): Unit =
    assertNormalizedOptions(
      """# comment only""",
      Seq.empty
    )

  private def assertIsolatedOption(providedOpt: String, expectedOpt: String = null): Unit =
    assertNormalizedOptions(providedOpt, Seq(Option(expectedOpt).getOrElse(providedOpt)))

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
    assertNormalizedOptions(
      "-sbt-dir /tmp\n-debug\n-color=always",
      Seq("-sbt-dir /tmp", "-debug", "-color=always")
    )

  @Test
  def parsesOptionsOnSeparateLinesWithWindowsLineEndings(): Unit =
    assertNormalizedOptions(
      "-sbt-dir /tmp\r\n-debug\r\n-color=always",
      Seq("-sbt-dir /tmp", "-debug", "-color=always")
    )

  @Test
  def parsesOptionsOnSeparateLinesWithCarriageReturnOnly(): Unit =
    assertNormalizedOptions(
      "-sbt-dir /tmp\r-debug\r-color=always",
      Seq("-sbt-dir /tmp", "-debug", "-color=always")
    )

  @Test
  def parsesMultiLineInputWithBlankLines(): Unit =
    assertNormalizedOptions(
      "\n-debug\n\n-color=always\n",
      Seq("-debug", "-color=always")
    )

  @Test
  def commentOnFirstLineKeepsFollowingLines(): Unit =
    assertNormalizedOptions(
      "-debug # comment\n-color=always\n-sbt-dir /tmp",
      Seq("-debug", "-color=always", "-sbt-dir /tmp")
    )

  @Test
  def commentLineAtStartKeepsFollowingLines(): Unit =
    assertNormalizedOptions(
      "# comment only\n-debug\n-color=always",
      Seq("-debug", "-color=always")
    )
}
