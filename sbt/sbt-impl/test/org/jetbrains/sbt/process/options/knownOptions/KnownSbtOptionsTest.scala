package org.jetbrains.sbt.process.options.knownOptions

import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptionArgMapping.MappedArguments
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit coverage for the known sbt option registry and its direct argument mappings.
 *
 * ## Test coverage
 * Primary coverage:
 * - [[KnownSbtOptions]]
 *
 * Indirect coverage:
 * - [[KnownSbtOption]]
 * - [[KnownSbtOptionArgMapping]]
 */
class KnownSbtOptionsTest {

  private val ProjectPath = "/tmp/project"

  @Test
  def mapsSupportedOptionsToArguments(): Unit = {
    val cases = Seq(
      MappingCase(
        "-sbt-boot /tmp/sbt-boot",
        parsedValue = Some("/tmp/sbt-boot"),
        expected = MappedArguments(vmOptions = Seq("-Dsbt.boot.directory=/tmp/sbt-boot"))
      ),
      MappingCase(
        "-sbt-dir /tmp/sbt-dir",
        parsedValue = Some("/tmp/sbt-dir"),
        expected = MappedArguments(vmOptions = Seq("-Dsbt.global.base=/tmp/sbt-dir"))
      ),
      MappingCase(
        "-ivy /tmp/ivy-cache",
        parsedValue = Some("/tmp/ivy-cache"),
        expected = MappedArguments(vmOptions = Seq("-Dsbt.ivy.home=/tmp/ivy-cache"))
      ),
      MappingCase(
        "-no-global",
        expected = MappedArguments(vmOptions = Seq(s"-Dsbt.global.base=$ProjectPath/project/.sbtboot"))
      ),
      MappingCase(
        "-no-share",
        expected = MappedArguments(
          vmOptions = Seq(
            "-Dsbt.global.base=project/.sbtboot",
            "-Dsbt.boot.directory=project/.boot",
            "-Dsbt.ivy.home=project/.ivy"
          )
        )
      ),
      MappingCase(
        "-jvm-debug 5005",
        parsedValue = Some("5005"),
        expected = MappedArguments(
          vmOptions = Seq("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
        )
      ),
      MappingCase(
        "-sbt-cache /tmp/sbt-cache",
        parsedValue = Some("/tmp/sbt-cache"),
        expected = MappedArguments(vmOptions = Seq("-Dsbt.global.localcache=/tmp/sbt-cache"))
      ),
      MappingCase(
        "-debug-inc",
        expected = MappedArguments(vmOptions = Seq("-Dxsbt.inc.debug=true"))
      ),
      MappingCase(
        "-traces",
        expected = MappedArguments(vmOptions = Seq("-Dsbt.traces=true"))
      ),
      MappingCase(
        "-timings",
        expected = MappedArguments(
          vmOptions = Seq(
            "-Dsbt.task.timings=true",
            "-Dsbt.task.timings.on.shutdown=true"
          )
        )
      ),
      MappingCase(
        "-no-colors",
        expected = MappedArguments(vmOptionsShellOnly = Seq("-Dsbt.log.noformat=true"))
      ),
      MappingCase(
        "-color=always",
        parsedValue = Some("always"),
        expected = MappedArguments(vmOptionsShellOnly = Seq("-Dsbt.color=always"))
      ),
      MappingCase(
        "-error",
        expected = MappedArguments(launcherArgs = Seq("--error"))
      ),
      MappingCase(
        "-warn",
        expected = MappedArguments(launcherArgs = Seq("--warn"))
      ),
      MappingCase(
        "-info",
        expected = MappedArguments(launcherArgs = Seq("--info"))
      ),
      MappingCase(
        "-debug",
        expected = MappedArguments(launcherArgs = Seq("--debug"))
      ),
      MappingCase(
        "-d",
        expected = MappedArguments(launcherArgs = Seq("--debug"))
      )
    )

    cases.foreach(assertMappedArguments)
  }

  @Test
  def findMatchingSpelling_SeparateValueOption_WithWhitespaceAndValue_ShouldMatchSpelling(): Unit =
    assertEquals(Some("-sbt-dir"), KnownSbtOptions.findMatchingSpelling("-sbt-dir /tmp").map(_._2.text))

  @Test
  def findMatchingSpelling_SeparateValueOption_NameWithExtraSuffix_ShouldNotMatchSpelling(): Unit =
    assertEquals(None, KnownSbtOptions.findMatchingSpelling("-sbt-dirop").map(_._2.text))

  @Test
  def findMatchingSpelling_SeparateValueOption_WithInlineDelimiter_ShouldNotMatchSpelling(): Unit =
    assertEquals(None, KnownSbtOptions.findMatchingSpelling("-sbt-dir=/tmp").map(_._2.text))

  private def assertMappedArguments(testCase: MappingCase): Unit = {
    val actual = KnownSbtOptions.findMatchingSpelling(testCase.rawOption).map { case (entry, _) =>
      entry.argMapping.toArguments(testCase.parsedValue, ProjectPath)
    }

    assertEquals(s"Unexpected mapping for ${testCase.rawOption}", Some(testCase.expected), actual)
  }

  private final case class MappingCase(
    rawOption: String,
    parsedValue: Option[String] = None,
    expected: MappedArguments
  )
}
