package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class TypeArgInferenceTest extends BadCodeGreenTestBase {
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
}
