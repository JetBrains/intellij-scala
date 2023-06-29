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
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ASTWrapperPsiElement(tCLI_DIRECTIVE_PREFIX)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveCommandImpl(tCLI_DIRECTIVE_COMMAND)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    ASTWrapperPsiElement(tCLI_DIRECTIVE_ERROR)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_ERROR)(',')\n    PsiWhiteSpace(' ')\n    ASTWrapperPsiElement(tCLI_DIRECTIVE_ERROR)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveValueImpl(tCLI_DIRECTIVE_VALUE)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.foo:bar:42')"
  )

  def test_valid_even_though_there_is_a_comma_after_key(): Unit = checkTree(
    "//> using dep, org.foo:bar:42",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ASTWrapperPsiElement(tCLI_DIRECTIVE_PREFIX)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveCommandImpl(tCLI_DIRECTIVE_COMMAND)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveKeyImpl(tCLI_DIRECTIVE_KEY)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    ASTWrapperPsiElement(tCLI_DIRECTIVE_COMMA)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveValueImpl(tCLI_DIRECTIVE_VALUE)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.foo:bar:42')"
  )
}
