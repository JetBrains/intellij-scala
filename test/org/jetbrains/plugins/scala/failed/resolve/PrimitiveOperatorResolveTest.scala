package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton.Yalyshev on 02/05/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class PrimitiveOperatorResolveTest extends FailedResolveCaretTestBase {

  def testSCL9645(): Unit = {
    doResolveCaretTest(
      """
        |class Base[+T](final val value: T)
        |
        |class Derived[+T](value: T) extends Base(value)
        |
        |abstract class Container[+E] {
        |  def element: E
        |}
        |
        |object Test {
        |  val c: Container[Derived[Int]] = ???
        |  c.element.value <caret>* 2
        |}
      """.stripMargin)
  }

  def testSCL11547(): Unit = {
    doResolveCaretTest(
      """
        |val a = List("A", "B", "B", "C").toSet.reduceOption(_ <caret>+ " " + _).get
      """.stripMargin)
  }
}
