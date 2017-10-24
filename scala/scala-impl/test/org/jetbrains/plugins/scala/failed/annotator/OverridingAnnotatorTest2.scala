package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class OverridingAnnotatorTest2 extends ScalaLightCodeInsightFixtureTestAdapter {

  //TODO: the issue does not reproduce when test is performed  using OverridingAnnotatorTest
  def testSCL3807(): Unit = {
    checkTextHasNoErrors(
      """
        |trait A {
        |  def foo(f: (=> A) => Int) = {_: A => 42}
        |}
        |
        |object A extends A{
        |  def foo(f: (A) => Int) = null
        |}
      """.stripMargin)
  }

  def testScl2071(): Unit = {
    checkTextHasNoErrors(
      """
        |  def doSmth(p: String) {}
        |  def doSmth(p: => String) {}
      """.stripMargin)
  }

  def testScl9034(): Unit = {
    checkTextHasNoErrors(
      """
        |  def apply(list: Iterable[Int]): ErrorHighlighting = { this ("int") }
        |  def apply(list: => Iterable[String]): ErrorHighlighting = { this ("string") }
      """.stripMargin)
  }
}
