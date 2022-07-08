package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class AlreadyDefinedTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL2101(): Unit =
    checkTextHasNoErrors(
      """
        |class Some(name: Int) {
        |    def name {""}
        |}
      """.stripMargin)

  def testSCL5789(): Unit =
    checkTextHasNoErrors(
      """
        |class Test {
        |  private[this] val x = 1
        |  def x() = 2
        |}
      """.stripMargin)
}
