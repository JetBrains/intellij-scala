package org.jetbrains.plugins.scala.lang.parser

class TraitParameterParserTest extends SimpleScala3ParserTestBase {
  def test_without_params(): Unit = checkParseErrors(
    "trait Test"
  )

  def test_one_trait_param(): Unit = checkParseErrors(
    "trait Test(arg: Int)"
  )

  def test_default_trait_param(): Unit = checkParseErrors(
    "trait Test(arg: Int, arg2: Boolean = true)"
  )

  def test_val_trait_param(): Unit = checkParseErrors(
    "trait Test(val member: Int)"
  )

  def test_two_parameter_clauses(): Unit = checkParseErrors(
    "trait Test(arg: Int)(val member: Int)"
  )

  def test_with_extends(): Unit = checkParseErrors(
    "trait Test(arg: Int) extends Base"
  )

  def test(): Unit = checkParseErrors(
    """
      |object testindent
      |
      |  class A
      |
      |  /* foo */ class B
      |
      |  class C
      |""".stripMargin
  )
}
