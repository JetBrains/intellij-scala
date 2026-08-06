package org.jetbrains.sbt.process.options.utils

import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

private[options] object SbtOptionsWarningAssertions {

  final case class WarningData(message: String, details: String)

  def assertWarnings(
    reporter: MessagesCollectingBuildReporter,
    expected: Seq[WarningData]
  ): Unit =
    assertCollectionEquals(
      renderWarningData(expected),
      renderWarningData(reporter.getWarnings.map(warning => WarningData(warning.message, warning.details)))
    )

  def assertNoWarnings(reporter: MessagesCollectingBuildReporter): Unit =
    assertWarnings(reporter, Seq.empty)

  val AllAvailableOptionsText: String =
    """All available options: 
      |-sbt-boot <path>
      |-sbt-dir <path>
      |-ivy <path>
      |-no-global
      |-no-share
      |-jvm-debug <port>
      |-sbt-cache <path>
      |-debug-inc
      |-traces
      |-timings
      |-no-colors
      |-color=auto|always|true|false|never
      |-error
      |-warn
      |-info
      |-debug
      |-d""".stripMargin

  private def renderWarningData(warnings: Seq[WarningData]): Seq[String] =
    warnings.map { warning =>
      s"""message:
         |${warning.message}
         |details:
         |${warning.details}""".stripMargin
    }
}
