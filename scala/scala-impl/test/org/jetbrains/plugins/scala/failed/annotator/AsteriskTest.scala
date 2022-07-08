package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class AsteriskTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL13016(): Unit = {
    checkTextHasNoErrors(
      """
        |class Test {
        |  def f(a : (Int*) => Unit): Unit = {
        |    a.apply(1, 2)
        |  }
        |}
      """.stripMargin)
  }
}
