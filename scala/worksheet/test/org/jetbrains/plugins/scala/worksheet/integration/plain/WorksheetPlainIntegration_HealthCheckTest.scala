package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.junit.Test

trait WorksheetPlainIntegration_HealthCheckTest { self: PlainWorksheetTestBase =>
  @Test
  def testSimple_1(): Unit = {
    fetchJLineForScala_2_13_0()

    val left =
      """val a = 1
        |val b = 2
        |""".stripMargin

    val right =
      """a: Int = 1
        |b: Int = 2""".stripMargin

    doRenderTest(left, right)
  }
}
