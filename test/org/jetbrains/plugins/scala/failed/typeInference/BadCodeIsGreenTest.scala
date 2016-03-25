package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * User: Dmitry.Naydanov
  * Date: 22.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class BadCodeIsGreenTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val START = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val END = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END
  
  def testScl7139_1() {
    checkTextHasError(
      s"""
        |class X1[${START}A >: A${END}]
      """.stripMargin, "‘A’ has itself as bound")
  }
  
  def testScl7139_2() {
    checkTextHasError(
      s"""
        |class X2[A <: B, B <: C, ${START}C <: A${END}]
      """.stripMargin, "‘A’ has itself as bound")
  }
  
  def testScl7139_3() {
    checkTextHasError(
      s"""
        |class X3[A, B, ${START}C >: A <: B${END}]
      """.stripMargin, "Lower bound doesn't conform to upper bound")
  }

  def testScl1731(): Unit = {
    checkTextHasError(
      s"""
         |object Test {
         |  class A
         |  class B
         |  val a: (=> A) => B = $START(x: A) => new B$END
         |}
       """.stripMargin, "Type mismatch: expected (=> TicketTester.A) => TicketTester.B, found: TicketTester.A => TicketTester.B")
  }

  def testScl8684(): Unit = {
    checkTextHasError(
      """object Test {
        |  def foo() = {
        |    for {
        |      x <- Future(1)
        |      y <- Option(1)
        |    } yield x + y
        |  }
        |}
      """.stripMargin, "Type mismatch: expected Future[?], found Option[Int]")
  }

  def testSCL4434(): Unit = {
    checkTextHasError(
      """
        |object Foo {
        |  def foo(a: Any) = a;
        |  var a = 1;
        |  foo(a = 2)
        |}
      """.stripMargin, "Reference to a is ambiguous; it is both a method parameter and a variable in scope.")
  }
}
