package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/29/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class ApplicationNotTakeParamImplicit extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL9931(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Foo {
        |  def foo(a: Int) = 1
        |}
        |
        |object Foo{
        |  def foo = 0.2
        |
        |  implicit def defImpl(x: Foo.type):Foo = FooImpl
        |}
        |
        |object FooImpl extends Foo
        |
        |object Bar {
        |  Foo.foo(1) //in (1): Application does not takes parameters
        |}
      """.stripMargin)
  }
}
