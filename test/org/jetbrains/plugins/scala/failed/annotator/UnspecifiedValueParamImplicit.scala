package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by user on 3/28/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class UnspecifiedValueParamImplicit extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL10045(): Unit = {
    checkTextHasNoErrors(
      """
        |class Repro {
        |    implicit val i: Int = 0
        |
        |    new ReproDep // Warning: "Unspecified value parameters: i: Int"
        |}
        |
        |class ReproDep(private implicit val i: Int)
      """.stripMargin)
  }
}
