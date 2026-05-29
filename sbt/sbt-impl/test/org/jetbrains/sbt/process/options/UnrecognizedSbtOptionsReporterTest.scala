package org.jetbrains.sbt.process.options

import org.jetbrains.sbt.process.options.parsing.model.{SbtOptionsSource, UnrecognizedSbtOption, UnrecognizedSbtOptions}
import org.jetbrains.sbt.process.options.utils.MessagesCollectingBuildReporter
import org.jetbrains.sbt.process.options.utils.SbtOptionsWarningAssertions.{AllAvailableOptionsText, WarningData, assertWarnings}
import org.junit.Test

/**
 * Unit coverage for warning rendering from unrecognized sbt option parser results.
 *
 * ## Test coverage
 * Primary coverage:
 * - [[UnrecognizedSbtOptionsReporter]]
 *
 * Indirect coverage:
 * - [[knownOptions.KnownSbtOptions]]
 * - [[parsing.model.UnrecognizedSbtOption]]
 * - [[parsing.model.UnrecognizedSbtOptions]]
 * - [[parsing.model.SbtOptionsSource]]
 */
class UnrecognizedSbtOptionsReporterTest {

  @Test
  def reportsWarningWithSuggestion(): Unit =
    assertReportedWarning(
      Seq(UnrecognizedSbtOption("-sbt-dirop", Some("-sbt-dir <path>"))),
      expected = Seq(
        WarningData(
          "unrecognized sbt option: -sbt-dirop",
          s"""Unrecognized sbt option: -sbt-dirop. Did you mean -sbt-dir <path> ? (Source: IDE settings)
             |$AllAvailableOptionsText""".stripMargin
        )
      )
    )

  @Test
  def reportsWarningWithoutSuggestion(): Unit =
    assertReportedWarning(
      Seq(UnrecognizedSbtOption("-totally-unknown", None)),
      expected = Seq(
        WarningData(
          "unrecognized sbt option: -totally-unknown",
          s"""Unrecognized sbt option: -totally-unknown (Source: IDE settings)
             |$AllAvailableOptionsText""".stripMargin
        )
      )
    )

  @Test
  def reportsWarningWithMultipleUnrecognisedOptions(): Unit =
    assertReportedWarning(
      Seq(
        UnrecognizedSbtOption("-sbt-dirop", Some("-sbt-dir <path>")),
        UnrecognizedSbtOption("-totally-unknown", None),
        UnrecognizedSbtOption("-color", Some("-color=auto|always|true|false|never"))
      ),
      expected = Seq(
        WarningData(
          "unrecognized sbt options: -sbt-dirop, -totally-unknown, -color",
          s"""Unrecognized sbt option: -sbt-dirop. Did you mean -sbt-dir <path> ? (Source: IDE settings)
             |Unrecognized sbt option: -totally-unknown (Source: IDE settings)
             |Unrecognized sbt option: -color. Did you mean -color=auto|always|true|false|never ? (Source: IDE settings)
             |$AllAvailableOptionsText""".stripMargin
        )
      )
    )

  @Test
  def reportsWarningSourceFromEverySupportedSource(): Unit = {
    val buildReporter = new MessagesCollectingBuildReporter
    val reporter = new UnrecognizedSbtOptionsReporter(buildReporter)
    val unrecognised = Seq(
      UnrecognizedSbtOptions(
        SbtOptionsSource.EnvironmentVariable,
        Seq(UnrecognizedSbtOption("-unknown-from-env", None))
      ),
      UnrecognizedSbtOptions(
        SbtOptionsSource.OptionsFile,
        Seq(UnrecognizedSbtOption("-unknown-from-file", None))
      ),
      UnrecognizedSbtOptions(
        SbtOptionsSource.IdeSettings,
        Seq(UnrecognizedSbtOption("-unknown-from-settings", None))
      )
    )

    reporter.reportUnrecognizedOptions(unrecognised)

    assertWarnings(
      buildReporter,
      Seq(
        WarningData(
          "unrecognized sbt option: -unknown-from-env",
          s"""Unrecognized sbt option: -unknown-from-env (Source: SBT_OPTS environment variable)
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "unrecognized sbt option: -unknown-from-file",
          s"""Unrecognized sbt option: -unknown-from-file (Source: .sbtopts file)
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "unrecognized sbt option: -unknown-from-settings",
          s"""Unrecognized sbt option: -unknown-from-settings (Source: IDE settings)
             |$AllAvailableOptionsText""".stripMargin
        )
      )
    )
  }

  private def assertReportedWarning(
    unrecognizedOptions: Seq[UnrecognizedSbtOption],
    expected: Seq[WarningData]
  ): Unit = {
    val buildReporter = new MessagesCollectingBuildReporter
    val reporter = new UnrecognizedSbtOptionsReporter(buildReporter)
    val unrecognised = Seq(UnrecognizedSbtOptions(SbtOptionsSource.IdeSettings, unrecognizedOptions))
    reporter.reportUnrecognizedOptions(unrecognised)

    assertWarnings(buildReporter, expected)
  }
}
