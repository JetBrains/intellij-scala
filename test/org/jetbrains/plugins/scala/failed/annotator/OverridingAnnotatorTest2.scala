package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class OverridingAnnotatorTest2 extends ScalaLightCodeInsightFixtureTestAdapter {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

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

  def testScl11112(): Unit = {
    checkTextHasNoErrors(
      """
        |  object Table {
        |    def apply[A](heading: String, rows: A*) = ???
        |    def apply[A, B](heading: (String, String), rows: (A, B)*) = ???
        |  }
        |
        |  object TableUser {
        |    Table(("One", "Two"), ("A", "B"))
        |  }
      """.stripMargin)
  }
}
