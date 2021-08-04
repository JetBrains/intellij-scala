package org.jetbrains.plugins.scala.lang.parser.incremental

class IncrementalParserTest extends IncrementalParserTestBase {

  def test_removing_opening_parenthesis_of_self_constructor_call(): Unit = doTest(
    s"""{
       |  def this() = {
       |    this$START($END)
       |  }
       |}
       |""".stripMargin
  )
}
