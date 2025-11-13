package org.jetbrains.plugins.scala.failed.resolve

class PrimitiveOperatorResolveTest extends FailedResolveCaretTestBase {
  def testSCL11547(): Unit = {
    doResolveCaretTest(
      """
        |val a = List("A", "B", "B", "C").toSet.reduceOption(_ <caret>+ " " + _).get
      """.stripMargin)
  }
}
