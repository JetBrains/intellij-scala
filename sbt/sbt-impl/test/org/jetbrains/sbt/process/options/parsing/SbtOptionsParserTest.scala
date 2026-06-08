package org.jetbrains.sbt.process.options.parsing

import org.jetbrains.sbt.process.options.parsing.model.ParsedSbtOption.{DefinedSbtOption, RawJvmSbtOption}
import org.jetbrains.sbt.process.options.parsing.model.SbtOptionsDiagnostic.Unrecognized
import org.jetbrains.sbt.process.options.parsing.model.{ParsedSbtOption, SbtOptionsSource, UnrecognizedSbtOption}
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit coverage for parser classification of normalized sbt option entries.
 *
 * ## Test coverage
 * Primary coverage:
 * - [[SbtOptionsParser]]
 *
 * Indirect coverage:
 * - [[org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptions]]
 * - [[org.jetbrains.sbt.process.options.parsing.model.ParsedSbtOption]]
 * - [[org.jetbrains.sbt.process.options.parsing.model.SbtOptionsDiagnostic]]
 * - [[org.jetbrains.sbt.process.options.parsing.model.UnrecognizedSbtOption]]
 * - [[org.jetbrains.sbt.process.options.parsing.model.SbtOptionsSource]]
 */
class SbtOptionsParserTest {

  @Test
  def parsesRawJvmOptions(): Unit = {
    val actual = parse("-J-Xmx2G", "-Dsbt.supershell=false")

    assertEquals(
      Seq(
        RawJvmSbtOption("-Xmx2G"),
        RawJvmSbtOption("-Dsbt.supershell=false")
      ),
      actual.parsed
    )
    assertEquals(Seq.empty, actual.diagnostics)
  }

  @Test
  def parsesKnownOptionsWithExpectedValueShapes(): Unit = {
    val actual = parse("-debug", "-color=always", "-sbt-dir /tmp/sbt")

    assertEquals(
      Seq(
        KnownOptionData("-debug", None),
        KnownOptionData("-color=", Some("always")),
        KnownOptionData("-sbt-dir", Some("/tmp/sbt"))
      ),
      extractKnownOptions(actual.parsed)
    )
    assertEquals(Seq.empty, actual.diagnostics)
  }

  @Test
  def rejectsOptionsWithWrongValueSyntax(): Unit = {
    val rawOptions = Seq("-debug=true", "-color", "-color always", "-sbt-dir=/tmp")
    val actual = parse(rawOptions *)

    assertEquals(Seq.empty, actual.parsed)
    assertEquals(
      Seq(SbtOptionsSource.IdeSettings),
      actual.diagnostics.map(_.source)
    )
    assertEquals(
      rawOptions,
      actual.diagnostics.flatMap {
        case Unrecognized(_, unrecognizedOptions, _) => unrecognizedOptions.map(_.rawOption)
        case other => throw new AssertionError(s"Expected only unrecognized diagnostics, got: $other")
      }
    )
  }

  @Test
  def keepsRecognisedOptionsWhenMixedWithInvalidOptions(): Unit = {
    val actual = parse("-debug-inc", "-color")

    assertEquals(
      Seq(KnownOptionData("-debug-inc", None)),
      extractKnownOptions(actual.parsed)
    )
    assertEquals(
      Seq(
        Unrecognized(
          SbtOptionsSource.IdeSettings,
          Seq(UnrecognizedSbtOption("-color", Some("-color=auto|always|true|false|never")))
        )
      ),
      actual.diagnostics
    )
  }

  @Test
  def reportsClosestKnownOptionForNearMiss(): Unit = {
    val actual = parse("-sbt-dirop", "-totally-unknown")

    assertEquals(Seq.empty, actual.parsed)
    assertEquals(
      Seq(
        Unrecognized(
          SbtOptionsSource.IdeSettings,
          Seq(
            UnrecognizedSbtOption("-sbt-dirop", Some("-sbt-dir <path>")),
            UnrecognizedSbtOption("-totally-unknown", None)
          )
        )
      ),
      actual.diagnostics
    )
  }

  private def parse(rawOptions: String*) =
    SbtOptionsParser.parse(rawOptions, SbtOptionsSource.IdeSettings)

  private def extractKnownOptions(parsedOptions: Seq[ParsedSbtOption]): Seq[KnownOptionData] =
    parsedOptions.map {
      case DefinedSbtOption(entry, parsedValue) =>
        KnownOptionData(entry.spelling.text, parsedValue)
      case other =>
        throw new AssertionError(s"Expected only known sbt options, got: $other")
    }

  private final case class KnownOptionData(spellingText: String, parsedValue: Option[String])
}
