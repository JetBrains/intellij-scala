package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by user on 3/28/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class UnspecifiedValueParamImplicitTest extends ScalaLightCodeInsightFixtureTestAdapter {
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

  def testSCL5768(): Unit = {
    checkTextHasNoErrors(
      """
        |object Foo {
        |    trait Tx
        |    trait Foo { def apply()(implicit tx: Tx): Bar }
        |    trait Bar { def update(value: Int): Unit }
        |
        |    def test(foo: Foo)(implicit tx: Tx) {
        |      foo()() = 1  // second () is highlighted red
        |    }
        |  }
      """.stripMargin)
  }

  def testSCL10845(): Unit = {
    checkTextHasNoErrors(
      """
        |object Utils {
        |
        |  implicit class FooPimps(f: Foo) {
        |    def implicitMethod(): Unit = println(s"implicit foo method")
        |  }
        |
        |}
        |
        |class Foo {
        |
        |  import Utils._
        |
        |  implicitMethod() // <- wrongly reports "Cannot resolve reference implicitMethod with such signature". "sbt run" works fine
        |
        |  def implicitMethod(x: String): Unit = ()
        |}
        |
        |object Main extends App {
        |  new Foo
        |}
      """.stripMargin)
  }
}
