package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class TypeArgInferenceTest extends BadCodeGreenTestBase {

  override protected def shouldPass: Boolean = false

  import CodeInsightTestFixture.CARET_MARKER

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
        |  processSpecificNetwork(fred, bar${CARET_MARKER}ney)
        |}
      """.stripMargin
    doTest(text)
  }

  def testScl6883(): Unit = {
    doTest("List(1, 2).reduce(_.toString + _.toString)")
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
        |  def foo(i: Iterator[String], s: Semigroup[String]) = i.fold(s.zero)(s.ap${CARET_MARKER}pend)
        |}""".stripMargin
    doTest(text)
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
          |  def foo(i: Iterator[String], s: Semigroup[String]) = i.fold(s.zero)(s.ap${CARET_MARKER}pend _)
          |}""".stripMargin
    doTest(text)
  }
}
