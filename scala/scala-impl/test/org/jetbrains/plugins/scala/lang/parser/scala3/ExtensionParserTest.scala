package org.jetbrains.plugins.scala.lang.parser.scala3

class ExtensionParserTest extends SimpleScala3ParserTestBase {

  def test_simple(): Unit = checkTree(
    """
      |extension (i: Int)
      |  def test = 0
      |""".stripMargin,
    """
      |""".stripMargin
  )

  def test_simple_on_next_line(): Unit = checkTree(
    """
      |extension (i: Int)
      |  def test = 0
      |""".stripMargin,
    """
      |""".stripMargin
  )
}
