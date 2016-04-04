package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/29/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class InfixTailRecursiveCall extends ScalaLightCodeInsightFixtureTestAdapter{
  def testSCL8792(): Unit = {
    checkTextHasNoErrors(
      """
        |import scala.annotation.tailrec
        |
        |case class Cursor(coord: Seq[Int]) {
        |
        |  def head = coord.head
        |
        |  def tail = Cursor(coord.tail)
        |
        |  @tailrec
        |  final def compare(b: Cursor): Int = {
        |    if (head == b.head) tail compare b.tail //Error only with infix notation
        |    else 0
        |  }
        |}
        |
      """.stripMargin
    )
  }
}
