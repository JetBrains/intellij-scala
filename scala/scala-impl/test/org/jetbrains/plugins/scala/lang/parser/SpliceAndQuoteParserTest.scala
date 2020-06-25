package org.jetbrains.plugins.scala.lang.parser

class SpliceAndQuoteParserTest  extends SimpleScala3ParserTestBase  {
  def test_spliceExpr(): Unit = checkParseErrors(
    "${ expr }"
  )

  def test_spliceStmts(): Unit = checkParseErrors(
    """
      |${
      |  val x = 38
      |  println(x)
      |}
      |""".stripMargin
  )

  def test_spliceTypeExpr(): Unit = checkParseErrors(
    "test: ${ tyexpr }"
  )

  def test_spliceIdentifier(): Unit = checkParseErrors(
    "$ident"
  )

  def test_spliceTypeIdentifier(): Unit = checkParseErrors(
    "test: $ident"
  )

  def test_quoteExpr(): Unit = checkParseErrors(
    "'{ expr }"
  )

  def test_quoteStmts(): Unit = checkParseErrors(
    """
      |'{
      |  val x = 38
      |  println(x)
      |}
      |""".stripMargin
  )

  def test_quoteTypeExpr(): Unit = checkParseErrors(
    "val x = '[ tyexpr ]"
  )

  def test_quoteIdentifier(): Unit = checkParseErrors(
    "'ident"
  )

  def test_quoteTypeIdentifier(): Unit = checkParseErrors(
    "test: 'ident"
  )
}
