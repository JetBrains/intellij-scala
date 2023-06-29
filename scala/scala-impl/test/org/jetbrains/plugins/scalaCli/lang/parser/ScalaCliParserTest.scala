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

  def test_comment_due_to_line_comment_prefix(): Unit = checkTree(
    "// //> using dep",
    "ScalaFile\n  PsiComment(comment)('// //> using dep')"
  )

  def test_invalid_due_to_comma_after_command(): Unit = checkTree(
    "//> using, dep org.jetbrains:annotations:24.0.1",
    ""
  )
}
