package org.jetbrains.plugins.scala.annotator

class IfConditionHighlightingTest extends ScalaHighlightingTestBase {
  def testSCL16304(): Unit = {
    val code =
      """
        |object Test {
        |  def foo: Boolean = false
        |  foo && {
        |    if(42) foo
        |    else   foo
        |  }
        |
        |  val a = if ("123") 42 else 43
        |}
        |""".stripMargin

    assertMessages(errorsFromScalaCode(code))(
      Error("42", "Expression of type Int doesn't conform to expected type Boolean"),
      Error("\"123\"", "Expression of type String doesn't conform to expected type Boolean")
    )
  }
}
