package org.jetbrains.sbt.process.options

import org.jetbrains.sbt.process.options.parsing.model.SbtOptionsDiagnostic.{Malformed, Unrecognized}
import org.jetbrains.sbt.process.options.parsing.model.{MalformedSbtOption, SbtOptionsDiagnostic, SbtOptionsSource, UnrecognizedSbtOption}
import org.jetbrains.sbt.process.options.utils.MessagesCollectingBuildReporter
import org.jetbrains.sbt.process.options.utils.SbtOptionsWarningAssertions.{AllAvailableOptionsText, WarningData, assertWarnings}
import org.junit.Test

import java.nio.file.Path

/**
 * Unit coverage for warning rendering from sbt option diagnostics.
 *
 * ## Test coverage
 * Primary coverage:
 * - [[SbtOptionsReporter]]
 *
 * Indirect coverage:
 * - [[knownOptions.KnownSbtOptions]]
 * - [[parsing.model.SbtOptionsDiagnostic]]
 * - [[parsing.model.UnrecognizedSbtOption]]
 * - [[parsing.model.MalformedSbtOption]]
 * - [[parsing.model.SbtOptionsSource]]
 */
class SbtOptionsReporterTest {

  @Test
  def reportsWarningWithSuggestion(): Unit =
    assertReportedWarning(
      Seq(UnrecognizedSbtOption("-sbt-dirop", Some("-sbt-dir <path>"))),
      expected = Seq(
        WarningData(
          "unrecognized sbt option: -sbt-dirop",
          s"""Unrecognized sbt option: -sbt-dirop. Did you mean -sbt-dir <path> ? (IDE settings)
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
          s"""Unrecognized sbt option: -totally-unknown (IDE settings)
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
          s"""Unrecognized sbt option: -sbt-dirop. Did you mean -sbt-dir <path> ? (IDE settings)
             |Unrecognized sbt option: -totally-unknown (IDE settings)
             |Unrecognized sbt option: -color. Did you mean -color=auto|always|true|false|never ? (IDE settings)
             |$AllAvailableOptionsText""".stripMargin
        )
      )
    )

  @Test
  def reportsWarningSourceFromEverySupportedSource(): Unit = {
    val buildReporter = new MessagesCollectingBuildReporter
    val reporter = new SbtOptionsReporter(buildReporter)
    val diagnostics = Seq(
      Unrecognized(
        SbtOptionsSource.EnvironmentVariable,
        Seq(UnrecognizedSbtOption("-unknown-from-env", None))
      ),
      Unrecognized(
        SbtOptionsSource.OptionsFile,
        Seq(UnrecognizedSbtOption("-unknown-from-file", None))
      ),
      Unrecognized(
        SbtOptionsSource.IdeSettings,
        Seq(UnrecognizedSbtOption("-unknown-from-settings", None))
      )
    )

    reporter.reportDiagnostics(diagnostics)

    assertWarnings(
      buildReporter,
      Seq(
        WarningData(
          "unrecognized sbt option: -unknown-from-env",
          s"""Unrecognized sbt option: -unknown-from-env (SBT_OPTS environment variable)
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "unrecognized sbt option: -unknown-from-file",
          s"""Unrecognized sbt option: -unknown-from-file (.sbtopts file)
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "unrecognized sbt option: -unknown-from-settings",
          s"""Unrecognized sbt option: -unknown-from-settings (IDE settings)
             |$AllAvailableOptionsText""".stripMargin
        )
      )
    )
  }

  @Test
  def reportsMalformedEnvironmentOptions(): Unit =
    assertReportedDiagnostics(
      Seq(
        Malformed(
          SbtOptionsSource.EnvironmentVariable,
          Seq(MalformedSbtOption(lineNumber = 1, unclosedQuote = '"', lineContent = """-sbt-dir "/env/sbt"""))
        )
      ),
      expected = Seq(
        WarningData(
          "malformed sbt option: -sbt-dir (SBT_OPTS environment variable)",
          "Malformed sbt option input: unbalanced double quote on line 1"
        )
      )
    )

  @Test
  def reportsMalformedOptionsFilePathAndLine(): Unit = {
    val optionsFile = Path.of("/tmp/project/.sbtopts")

    assertReportedDiagnostics(
      Seq(
        Malformed(
          SbtOptionsSource.OptionsFile,
          Seq(
            MalformedSbtOption(lineNumber = 3, unclosedQuote = '"', lineContent = """-sbt-dir "/file/sbt"""),
            MalformedSbtOption(lineNumber = 5, unclosedQuote = '\'', lineContent = """-ivy '/file/ivy""")
          ),
          optionsFile = Some(optionsFile)
        )
      ),
      expected = Seq(
        WarningData(
          "malformed sbt option: -sbt-dir (.sbtopts file)",
          s"""Malformed sbt option input: unbalanced double quote at:
             |${optionsFile.toAbsolutePath.toUri}:3
             |-sbt-dir "/file/sbt""".stripMargin
        ),
        WarningData(
          "malformed sbt option: -ivy (.sbtopts file)",
          s"""Malformed sbt option input: unbalanced single quote at:
             |${optionsFile.toAbsolutePath.toUri}:5
             |-ivy '/file/ivy""".stripMargin
        )
      )
    )
  }

  @Test
  def reportsMalformedIdeSettingsOptionsWithQuickFixAction(): Unit =
    assertReportedDiagnostics(
      Seq(
        Malformed(
          SbtOptionsSource.IdeSettings,
          Seq(MalformedSbtOption(lineNumber = 1, unclosedQuote = '"', lineContent = """-sbt-dir "/settings/sbt"""))
        )
      ),
      expected = Seq(
        WarningData(
          "malformed sbt option: -sbt-dir (IDE settings)",
          """Malformed sbt option input: unbalanced double quote on line 1
            |<a href="open_sbt_settings">Open Settings</a>""".stripMargin
        )
      )
    )

  @Test
  def reportsMalformedAssignmentOptionsWithoutValueInTitle(): Unit =
    assertReportedDiagnostics(
      Seq(
        Malformed(
          SbtOptionsSource.OptionsFile,
          Seq(
            MalformedSbtOption(lineNumber = 1, unclosedQuote = '"', lineContent = """K2="V2"""),
            MalformedSbtOption(lineNumber = 2, unclosedQuote = '"', lineContent = """K3="V3""")
          ),
          optionsFile = Some(Path.of("/tmp/project/.sbtopts"))
        ),
        Malformed(
          SbtOptionsSource.IdeSettings,
          Seq(MalformedSbtOption(lineNumber = 1, unclosedQuote = '"', lineContent = """KK="VV"""))
        )
      ),
      expected = Seq(
        WarningData(
          "malformed sbt option: K2= (.sbtopts file)",
          """Malformed sbt option input: unbalanced double quote at:
            |file:///tmp/project/.sbtopts:1
            |K2="V2""".stripMargin
        ),
        WarningData(
          "malformed sbt option: K3= (.sbtopts file)",
          """Malformed sbt option input: unbalanced double quote at:
            |file:///tmp/project/.sbtopts:2
            |K3="V3""".stripMargin
        ),
        WarningData(
          "malformed sbt option: KK= (IDE settings)",
          """Malformed sbt option input: unbalanced double quote on line 1
            |<a href="open_sbt_settings">Open Settings</a>""".stripMargin
        )
      )
    )

  @Test
  def reportsUnrecognizedDiagnosticsBeforeMalformedDiagnostics(): Unit =
    assertReportedDiagnostics(
      Seq(
        Malformed(
          SbtOptionsSource.EnvironmentVariable,
          Seq(MalformedSbtOption(lineNumber = 1, unclosedQuote = '"', lineContent = """-sbt-dir "/env/sbt"""))
        ),
        Unrecognized(
          SbtOptionsSource.IdeSettings,
          Seq(UnrecognizedSbtOption("-unknown-from-settings", None))
        )
      ),
      expected = Seq(
        WarningData(
          "unrecognized sbt option: -unknown-from-settings",
          s"""Unrecognized sbt option: -unknown-from-settings (IDE settings)
             |$AllAvailableOptionsText""".stripMargin
        ),
        WarningData(
          "malformed sbt option: -sbt-dir (SBT_OPTS environment variable)",
          "Malformed sbt option input: unbalanced double quote on line 1"
        )
      )
    )

  private def assertReportedWarning(
    unrecognizedOptions: Seq[UnrecognizedSbtOption],
    expected: Seq[WarningData]
  ): Unit =
    assertReportedDiagnostics(
      Seq(Unrecognized(SbtOptionsSource.IdeSettings, unrecognizedOptions)),
      expected
    )

  private def assertReportedDiagnostics(
    diagnostics: Seq[SbtOptionsDiagnostic],
    expected: Seq[WarningData]
  ): Unit = {
    val buildReporter = new MessagesCollectingBuildReporter
    val reporter = new SbtOptionsReporter(buildReporter)

    reporter.reportDiagnostics(diagnostics)

    assertWarnings(buildReporter, expected)
  }
}
