package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class DefaultArgWithTypeArgsTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL8688(): Unit = {
    checkTextHasNoErrors(
      """class Test {
        |  def foo[A, B](f: A => B = (a: A) => a) = ???
        |}
      """.stripMargin)
  }
}
