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

  def testScl6729(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Foo
        |
        |trait Bar {
        |  def foo: Foo = _
        |}
        |
        |trait Baz extends Bar {
        |  override object foo extends Foo
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


}
