package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Nikolay.Tropin
  */
class TypeArgInferenceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testScl9402(): Unit = {
    val text =
      s"""object ErrorHighlightingExample extends App {
        |  import scala.language.existentials
        |
        |  class Network {
        |
        |    class Member(val name: String)
        |
        |    def join(name: String) = ???
        |  }
        |
        |  def processSpecificNetwork[M <: n.Member forSome {val n : Network}](m1: M, m2: M) = (m1, m2)
        |
        |  val chatter = new Network
        |  val myFace = new Network
        |
        |  val fred: chatter.Member = chatter.join("Fred")
        |  val barney: myFace.Member = myFace.join("Barney")
        |
        |  processSpecificNetwork(fred, bar${CARET}ney)
        |}
      """.stripMargin
    checkHasErrorAroundCaret(text)
  }

  def testScl6883(): Unit = {
    checkHasErrorAroundCaret("List(1, 2).reduce(_.toString + _.toString)")
  }

  def testSCL10395(): Unit = {
    val text =
      s"""trait Semigroup[F] {
        |  def zero: F = ???
        |
        |  def append(f1: F, f2: => F): F = ???
        |}
        |
        |object Test {
        |  def foo(i: Iterator[String], s: Semigroup[String]) = i.fold(s.zero)(s.ap${CARET}pend)
        |}""".stripMargin
    checkHasErrorAroundCaret(text)
  }

  def testSCL10395_2(): Unit = {
    val text =
      s"""trait Semigroup[F] {
          |  def zero: F = ???
          |
        |  def append(f1: F, f2: => F): F = ???
          |}
          |
        |object Test {
          |  def foo(i: Iterator[String], s: Semigroup[String]) = i.fold(s.zero)(s.ap${CARET}pend _)
          |}""".stripMargin
    checkHasErrorAroundCaret(text)
  }
}
