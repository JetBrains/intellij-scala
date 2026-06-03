package org.jetbrains.plugins.scala.failed.resolve

class ApplyResolveTest extends FailedResolveCaretTestBase {

  def testSCL13705(): Unit = {
    doResolveCaretTest(
      """
        |case class Test(c: String)
        |
        |trait Factory {
        |  def apply(c: String): String = c
        |}
        |
        |object Test extends Factory {
        |  Test("").<caret>toLowerCase
        |}
      """.stripMargin)
  }
}
