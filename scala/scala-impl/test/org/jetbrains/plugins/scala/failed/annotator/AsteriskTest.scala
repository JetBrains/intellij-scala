package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Created by Anton.Yalyshev on 06/09/18.
  */

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
