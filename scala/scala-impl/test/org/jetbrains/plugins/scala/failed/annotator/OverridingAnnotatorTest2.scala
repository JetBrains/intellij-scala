package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
class OverridingAnnotatorTest2 extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

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

  def testScl9034(): Unit = {
    checkTextHasNoErrors(
      """
        |  def apply(list: Iterable[Int]): ErrorHighlighting = { this ("int") }
        |  def apply(list: => Iterable[String]): ErrorHighlighting = { this ("string") }
      """.stripMargin)
  }

  def testScl12605(): Unit = {
    checkTextHasNoErrors(
      """
        |class Bug {
        |  def main(args: Array[String]): Unit = {
        |    val bug = new Bug()
        |    bug.buggy(bug, (x, y) => x + y)
        |  }
        |
        |  def buggy(y: Bug): Bug = ???
        |
        |  def buggy(y: Bug, function: DDFunction): Bug = ???
        |}
        |
        |trait DDFunction {
        |  def apply(x: Double, y: Double): Double
        |}
      """.stripMargin)
  }

  def testScl13039(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Test[T] {
        |  def foo[S](x : T) : Unit = {
        |    val t = new Test[S] {
        |      override def foo[U](x: S): Unit = { }
        |    }
        |  }
        |}
      """.stripMargin)
  }

}
