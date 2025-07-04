package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class MultipleAndInterleavedUsingClausesTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_7

  def testSimpleLeading(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Foo[A]
      |  given Foo[String] = ???
      |  def f[A](using Foo[A])(a: A): A = ???
      |  val s: String = f("")
      |}
      |""".stripMargin
  )

  def testSimpleLeadingNeg(): Unit = checkHasErrorAroundCaret(
    s"""
      |object A {
      |  trait Foo[A]
      |  given Foo[Int] = ???
      |  def f[A](using Foo[A])(a: A): A = 123
      |  val s: String = f("$CARET")
      |}
      |""".stripMargin
  )

  def testMultipleLeading(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  given String = ???
       |  trait Foo[A] { type X }
       |  given Foo[Int] { type X = String }
       |  def f[A](using f: Foo[A])(using f.X)(a: A): f.X = ???
       |  val s: String = f(123)
       |}
       |""".stripMargin
  )

  def testSimpleInterleaving(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  given String = ???
      |  trait Foo[A] { type X }
      |  given Foo[Int] { type X = String }
      |  trait Bar[B, A]
      |  given Bar[Double, Int] = ???
      |  def f[A, B](using f: Foo[A])(using f.X)(a: A)(using b: Bar[B, A]): B = ???
      |  val s: Double = f(123)
      |}
      |""".stripMargin
  )

    //@TODO: fix, see https://youtrack.jetbrains.com/issue/SCL-23347 comment
//  def testSCL23347(): Unit = checkTextHasNoErrors(
//    """
//      |case class Bar(value: Int):
//      |  def foo(): Unit = ???
//      |  def foo(lambda: (String ?=> Bar => Int)): Unit = ???
//      |
//      |extension (target: Bar)
//      |  def ext(): Unit = ???
//      |  def ext(lambda: (String ?=> Bar => Int)): Unit = ???
//      |
//      |@main def myMain(): Unit =
//      |  val bar = Bar(1)
//      |
//      |  bar.foo(_.value)
//      |  bar.foo(b => b.value)
//      |
//      |  bar.ext(_.value)
//      |  bar.ext(b => b.value)
//      |""".stripMargin
//  )

  def testOverloadingExplicitLeading(): Unit = doResolveTest(
    s"""
       |object A {
       |  def f${REFTGT}oo(using s: String)(x: Int): Int = 123
       |  def foo(x:Int): String = ???
       |  val oo = f${REFSRC}oo(using "")(1)
       |}
       |""".stripMargin
  )

  def testOverloadingLeading(): Unit = doResolveTest(
    s"""
       |object A {
       |  trait Foo[A]
       |  given Foo[String] = ???
       |  def fo${REFTGT}o[A](using Foo[A])(a: A): A = ???
       |  def foo(x: String): Int = 13
       |  val x = fo${REFSRC}o(1)
       |}
       |""".stripMargin
  )

  def testSimpleInterleavingOverloading(): Unit = doResolveTest(
    s"""
       |object A {
       |  def b${REFTGT}ar[A](x: A)(using String)(y: Int): Int = 1
       |  def bar[A](x: A)(y: A): Int = 1
       |  b${REFSRC}ar(1)(1)
       |}
       |""".stripMargin
  )

  def testSCL22190(): Unit = checkTextHasNoErrors(
    """
      |def foo[A](using i: Int)(body: Int => A): A = ???
      |def bar[A](body: Int => A)(using i: Int): A = ???
      |
      |def main(): Unit =
      |  given Int = 7
      |
      |  foo { int =>
      |    assert(1 * int == 7)
      |  }
      |
      |  foo(using 42) { int =>
      |    assert(1 * int == 7)
      |  }
      |
      |  bar { int =>
      |    assert(1 * int == 7)
      |  }
      |""".stripMargin
  )
}
