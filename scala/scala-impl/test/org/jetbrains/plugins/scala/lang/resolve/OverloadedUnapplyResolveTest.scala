package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class OverloadedUnapplyResolveTest
    extends ScalaLightCodeInsightFixtureTestAdapter
    with SimpleResolveTestBase {
  import SimpleResolveTestBase._

  def testSCL9437(): Unit = doResolveTest(
    s"""
      |trait Parser[+T]
      |
      |sealed trait Result[+T]
      |
      |object Result{
      |
      |  case class Success[+T](value: T, index: Int) extends Result[T]
      |
      |  case class Failure(input: String,
      |                     index: Int,
      |                     lastParser: Parser[_],
      |                     traceData: (Int, Parser[_])) extends Result[Nothing]
      |
      |  object Failure {
      |    def un${REFTGT}apply[T](x: Result[T]): Option[(Parser[_], Int)] = ???
      |  }
      |}
      |
      |object Scl9437_Qualified {
      |  def test(): Unit = {
      |    val x: Result[_] = ???
      |    x match {
      |      case Result.Fa${REFSRC}ilure(x, y) =>
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def testSCL7279(): Unit = doResolveTest(
    s"""
       |object & {
       |  def un${REFTGT}apply[A](a: A): Option[(A, A)] = Some(a, a)
       |  def unapply[A,B](tup: Tuple2[A,B]): Option[(A, B)] = Some(tup)
       |}
       |
       |object SCL7279 {
       |  1 match {
       |    case 1 $REFSRC& a =>
       |  }
       |}""".stripMargin
  )

  def testSCL17567(): Unit = doResolveTest(
    s"""
       |case class Data(n: Int, s: String)
       |def process(t: (Data, Data)): Unit = ???
       |val data = Data(1, "1")
       |val tuple = (Data(1, "1"), Data(2, "2"))
       |val list = List(1, 2, 3)
       |object :<: {
       |  def unapply(arg: Data): Option[(Int, String)] = {
       |    Some((arg.n, arg.s))
       |  }
       |  def un${REFTGT}apply[T](arg: List[T]): Option[(T, List[T])] = {
       |    Some((arg.head, arg.tail))
       |  }
       |}
       |data match {
       |  case n :<: s => ()
       |}
       |list match {
       |  case head :$REFSRC<: tail => ()
       |}
       |""".stripMargin
  )
}
