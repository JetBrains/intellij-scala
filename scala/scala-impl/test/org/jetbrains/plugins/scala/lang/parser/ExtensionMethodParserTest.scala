package org.jetbrains.plugins.scala.lang.parser

class ExtensionMethodParserTest extends SimpleParserTestBase {

  def test_simple(): Unit = checkParseErrors(
    "def (x: Int) test (param: String): Unit = ()"
  )

  def test_wrong_multiple_params_but_parse_anyway(): Unit = checkParseErrors(
    "def (x: Int, y: Int) test (param: String): Unit = ()"
  )
}
