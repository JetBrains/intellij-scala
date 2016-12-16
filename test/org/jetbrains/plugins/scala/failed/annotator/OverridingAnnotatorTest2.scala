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

  val START = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val END = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END

  def testScl7536() {
    checkTextHasError(
      s"""
         |class Abs(var name: String){ }         |
         |class AbsImpl(${START}override${END} var name: String) extends Abs(name){ }
      """.stripMargin, "overriding variable name in class Abs of type String")
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

  def testScl11063(): Unit = {
    checkTextHasNoErrors(
      """
        |import scala.collection.mutable
        |
        |class DynamicMap[A](val self: mutable.Map[String, A]) extends AnyVal {
        |  def apply(key: Int): A = self(key.toString)
        |  def apply(key: Float): A = self(key.toString)
        |
        |  def update(key: String, value: A): Unit = self(key) = value
        |}
        |
        |object Example {
        |  val map = new DynamicMap(new mutable.HashMap[String, Int])
        |  <caret>map("a") = 5
        |}
      """.stripMargin)
  }
}
