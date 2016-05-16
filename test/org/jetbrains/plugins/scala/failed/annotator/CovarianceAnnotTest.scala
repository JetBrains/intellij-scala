package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 16/05/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class CovarianceAnnotTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL10263(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Foo[+A] {
        |  trait Bar
        |}
        |
        |trait Baz[+A] {
        |  val x: Foo[A]#Bar
        |}
      """.stripMargin
    )
  }
}