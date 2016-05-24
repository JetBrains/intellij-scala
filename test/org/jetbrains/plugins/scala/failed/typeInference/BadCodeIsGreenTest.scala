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

  def testSCL7618(): Unit = {
    checkTextHasError(
      """
        |object Main extends App {
        |  trait A[T]
        |  trait C[T]
        |  trait E extends A[Int]
        |  trait F extends C[Int]
        |  class B1 extends E with F
        |  class B2 extends F with E
        |  class B3 extends A[Int] with C[Int]
        |  class B4 extends C[Int] with A[Int]
        |  def foo[T, M[_] <: A[_]](x: M[T]): M[T] = x
        |
        |  foo(new B1)
        |  foo(new B2)
        |  foo(new B3)
        |  foo(new B4)
        |}
      """.stripMargin, "Type mismatch, expected: M[T], actual: Main.B4")
  }

  def testSCL8983(): Unit = {
    checkTextHasError(
      """
        |class Foo extends ((String,String) => String) with Serializable{
        |  override def apply(v1: String, v2: String): String = {
        |    v1+v2
        |  }
        |}
        |
        |object main extends App {
        |  val x = "x"
        |  val y = "y"
        |  val string: Foo = new Foo()(x,y)
        |}
      """.stripMargin, "Type mismatch, expected: Foo, actual: String")
  }

  def testSCL10320(): Unit = {
    checkTextHasError(
      """
        |object Hello {
        |  val foobar = new Foobar()
        |  foobar.notRed(Array(classOf[Hello.type], classOf[Object]))
        |}
        |
        |class Foobar {
        |  def notRed(someArray: Array[Class[_]]): Foobar = {
        |    someArray foreach (c ⇒ println(c.getCanonicalName))
        |    this
        |  }
        |}
      """.stripMargin, "class type required but Hello.type found")
  }
}
