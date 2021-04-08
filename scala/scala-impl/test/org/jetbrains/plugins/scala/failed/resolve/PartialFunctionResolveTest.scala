package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by Anton Yalyshev on 15/04/16.
  */
class PartialFunctionResolveTest extends FailedResolveCaretTestBase {

  def testSCL5464(): Unit = {
    doResolveCaretTest(
      """
        |class A {
        |    def m() {  }
        |    def m(i: Int) { }
        |  }
        |
        |  object Main extends App {
        |    val fun = (_: A).<caret>m _
        |  }
      """.stripMargin)
  }

  def testSCL11567(): Unit = {
    doResolveCaretTest(
      """
        |class mc {
        |  val a = <caret>println _
        |}
      """.stripMargin)
  }

  def testSCL13086(): Unit = {
    doResolveCaretTest(
      """
        |val x: String => Int => Char = _.<caret>charAt _
      """.stripMargin)
  }
}