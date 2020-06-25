package org.jetbrains.plugins.scala
package lang
package parser

class Scala3InlineParserTest extends SimpleScala3ParserTestBase {
  def test_inline_if(): Unit = checkParseErrors(
    "inline if (a > b) 1 else 2"
  )

  def test_inline_match(): Unit = checkParseErrors(
    "inline (1 - 2) * 3 match { case _ => () }"
  )

  def test_local_inline_def(): Unit = checkParseErrors(
    """
      |object Test {
      |  def foo() = {
      |    inline def bar: Int = foo(0)
      |  }
      |}
      |""".stripMargin
  )
}
