package org.jetbrains.plugins.scala.annotator

/**
  * Created by kate on 3/29/16.
  */
class InfixTailRecursiveCall extends AnnotatorLightCodeInsightFixtureTestAdapter {
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
