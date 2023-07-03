package org.jetbrains.plugins.scalaCli.lang.parser

import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase

class ScalaCliParserTest extends SimpleScalaParserTestBase {

  def test_comment_due_to_comma_between_prefix_and_command1(): Unit = checkTree(
    "//>, using dep org.foo:bar:42",
    "ScalaFile\n  PsiComment(comment)('//>, using dep org.foo:bar:42')"
  )

  def test_comment_due_to_comma_between_prefix_and_command2(): Unit = checkTree(
    "//> ,using dep org.foo:bar:42",
    "ScalaFile\n  PsiComment(comment)('//> ,using dep org.foo:bar:42')"
  )

  def test_comment_due_to_comma_between_prefix_and_command3(): Unit = checkTree(
    "//> , using dep org.foo:bar:42",
    "ScalaFile\n  PsiComment(comment)('//> , using dep org.foo:bar:42')"
  )

  def test_comment_due_to_line_comment_prefix1(): Unit = checkTree(
    "// //> using dep",
    "ScalaFile\n  PsiComment(comment)('// //> using dep')"
  )

  def test_comment_due_to_line_comment_prefix2(): Unit = checkTree(
    "///> using dep",
    "ScalaFile\n  PsiComment(comment)('///> using dep')"
  )

  def test_invalid_due_to_comma_after_command(): Unit = checkTree(
    "//> using, dep org.foo:bar:42",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiErrorElement:Scala CLI key expected: option, dep, jar, etc.\n      <empty list>\n    ScCliDirectiveToken(tCLI_DIRECTIVE_ERROR)(',')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.foo:bar:42')"
  )

  def test_valid_even_though_there_is_a_comma_after_key(): Unit = checkTree(
    "//> using dep, org.foo:bar:42",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.foo:bar:42')"
  )
}
