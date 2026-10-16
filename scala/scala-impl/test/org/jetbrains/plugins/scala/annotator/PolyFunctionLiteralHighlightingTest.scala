package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class PolyFunctionLiteralHighlightingTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3

  // in a block, the body of a polymorphic function literal is parsed as a block without braces
  def testInBlock(): Unit = assertNoErrors(
    """object Test {
      |  val id: [U] => U => U = { [U] => (u: U) => u }
      |
      |  def usingSeq[B](f: [A] => Seq[A] => B): B = f(Nil)
      |  usingSeq { [A] => (a: Seq[A]) =>
      |    a
      |  }
      |
      |  def boo(f: [A] => () => Unit): Unit = ???
      |  boo { [A] => () =>
      |    println(1)
      |    println(2)
      |  }
      |}
      |""".stripMargin
  )

  def testPlaceholderSyntax(): Unit = assertNoErrors(
    """object Test {
      |  val size = [A] => (_: List[A]).size
      |}
      |""".stripMargin
  )

  def testWithoutValueParameter(): Unit = assertErrorsText(
    """object Test {
      |  val f = [A] => 1
      |}
      |""".stripMargin,
    "Error(1,Polymorphic function literals must have a value parameter)"
  )
}
