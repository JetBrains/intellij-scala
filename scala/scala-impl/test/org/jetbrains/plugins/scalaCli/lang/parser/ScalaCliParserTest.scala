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

  // Not implemented yet, so for now expected to fail.
  // Basically, in the lexer `// ` should be a terminator for directives.
  // Probably likewise for `/*` but that still needs to be confirmed against scala-cli.
  def test_comment_after_directive(): Unit = checkTree(
    "//> using dep foo // foo",
    "ScalaFile: Dummy.scala(0,24)\n  PsiElement(SCALA_CLI_DIRECTIVE)(0,17)\n    ASTWrapperPsiElement(tCLI_DIRECTIVE_PREFIX)(0,3)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')(0,3)\n    PsiWhiteSpace(' ')(3,4)\n    ScCliDirectiveCommandImpl(tCLI_DIRECTIVE_COMMAND)(4,9)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')(4,9)\n    PsiWhiteSpace(' ')(9,10)\n    ScCliDirectiveKeyImpl(tCLI_DIRECTIVE_KEY)(10,13)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')(10,13)\n    PsiWhiteSpace(' ')(13,14)\n    ScCliDirectiveValueImpl(tCLI_DIRECTIVE_VALUE)(14,17)\n      ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')(14,17)\n  PsiWhiteSpace(' ')(17,18)\n  PsiComment(comment)('// foo')(18,24)"
  )
}
