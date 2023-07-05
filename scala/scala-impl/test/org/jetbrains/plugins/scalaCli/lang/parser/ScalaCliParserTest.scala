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

  def test_invalid_due_to_missing_key(): Unit = checkTree(
    "//> using",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiErrorElement:Scala CLI key expected: option, dep, jar, etc.\n      <empty list>"
  )

  def test_valid_even_though_there_is_a_comma_after_key(): Unit = checkTree(
    "//> using dep, org.foo:bar:42",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.foo:bar:42')"
  )

  def test_using_directive_without_value1(): Unit = checkTree(
    "//> using dep",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')"
  )

  def test_using_directive_without_value2(): Unit = checkTree(
    "//>  using dep",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace('  ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')"
  )

  def test_using_directive_without_value3(): Unit = checkTree(
    "//> using  dep",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace('  ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')"
  )

  def test_using_directive_with_value1(): Unit = checkTree(
    "//> using dep foo",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')"
  )

  def test_using_directive_with_value2(): Unit = checkTree(
    "//> using dep  foo",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace('  ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')"
  )

  def test_using_directive_with_space_separated_values1(): Unit = checkTree(
    "//> using dep foo bar",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')"
  )

  def test_using_directive_with_space_separated_values2(): Unit = checkTree(
    "//> using dep foo  bar",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')\n    PsiWhiteSpace('  ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')"
  )

  def test_using_directive_with_comma_separated_values1(): Unit = checkTree(
    "//> using dep foo,bar",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')"
  )

  def test_using_directive_with_comma_separated_values2(): Unit = checkTree(
    "//> using dep foo, bar",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')"
  )

  def test_using_directive_with_comma_separated_values3(): Unit = checkTree(
    "//> using dep foo ,bar",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')"
  )

  def test_using_directive_with_comma_separated_values4(): Unit = checkTree(
    "//> using dep foo , bar",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')"
  )

  def test_using_directive_with_comma_and_space_separated_values(): Unit = checkTree(
    "//> using dep foo,bar fizz",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('fizz')"
  )

  def test_do_not_bind_directive_to_class(): Unit = checkTree(
    "//> using dep foo\nclass Foo",
    "ScalaFile\n  PsiElement(SCALA_CLI_DIRECTIVE)\n    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')\n    PsiWhiteSpace(' ')\n    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')\n  PsiWhiteSpace('\\n')\n  ScClass: Foo\n    AnnotationsList\n      <empty list>\n    Modifiers\n      <empty list>\n    PsiElement(class)('class')\n    PsiWhiteSpace(' ')\n    PsiElement(identifier)('Foo')\n    PrimaryConstructor\n      AnnotationsList\n        <empty list>\n      Modifiers\n        <empty list>\n      Parameters\n        <empty list>\n    ExtendsBlock\n      <empty list>"
  )
}
