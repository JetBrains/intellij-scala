package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author anton.yalyshev
  * @since 14.04.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ApplyConfomanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL5660(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Test {
         |  var test: Option[String] = None
         |  def test_=(test: String) { this.test = Some(test) }
         |}
         |
         |(new Test).test = "test"
         |/* True */
      """.stripMargin)
  }
}
