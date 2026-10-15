package org.jetbrains.plugins.scala.lang.formatter.scalafmt

import org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.util.Markers
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * The `AvoidInfix` rewrite rule is only applied by scalafmt, the IntelliJ formatter keeps the infix call.
 */
@RunWith(classOf[JUnit4])
class ScalaFmtEnforcedRangeFormattingTest extends ScalaFmtTestBase with Markers {

  override def setUp(): Unit = {
    super.setUp()
    setScalafmtConfig("avoidInfix.conf")
    scalaSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = true
  }

  private def before =
    s"""class C {
       |  ${startMarker}def foo = 1 to 42$endMarker
       |}
       |""".stripMargin

  @Test
  def rangeFormattedWithIntellijFormatter(): Unit =
    doTextTest(
      before,
      """class C {
        |  def foo = 1 to 42
        |}
        |""".stripMargin
    )

  @Test
  def rangeFormattedWithScalafmtWhenEnforced(): Unit =
    ScalaFmtPreFormatProcessor.enforcingScalaFmtRangeFormatting {
      doTextTest(
        before,
        """class C {
          |  def foo = 1.to(42)
          |}
          |""".stripMargin
      )
    }
}
