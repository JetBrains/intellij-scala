package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 21/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class PatternResolveTest extends FailedResolveCaretTestBase {

  def testSCL5895(): Unit = {
    doResolveCaretTest(
      """
        |  case class Bar[T](wrapped: T) {
        |    def method(some: T) = ???
        |  }
        |
        |  def bar(fooTuple: (Bar[T], T) forSome { type T }) = fooTuple match {
        |    case (a, b) => a.<caret>method(b)
        |  }
      """.stripMargin)

  }

  def testSCL10735(): Unit = {
    doResolveCaretTest(
      """
        |sealed trait ST[T]
        |case object A extends ST[String]
        |case object B extends ST[Boolean]
        |case class Value[T](obj: ST[T], value: T)
        |
        |object Main extends App {
        |  val v: Value[_] = Value(A, "wtf")
        |  val res = v match {
        |    case Value(A, x) => x.<caret>split(",")
        |  }
        |}
      """.stripMargin)

  }
}
