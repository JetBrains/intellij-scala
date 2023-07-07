package org.jetbrains.plugins.scalaCli.lang.parser

import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase

class ScalaCliParserTest extends SimpleScalaParserTestBase {

  def test_comment_due_to_comma_between_prefix_and_command1(): Unit = checkTree(
    "//>, using dep org.foo:bar:42",
    """ScalaFile
      |  PsiComment(comment)('//>, using dep org.foo:bar:42')""".stripMargin
  )

  def test_comment_due_to_comma_between_prefix_and_command2(): Unit = checkTree(
    "//> ,using dep org.foo:bar:42",
    """ScalaFile
      |  PsiComment(comment)('//> ,using dep org.foo:bar:42')""".stripMargin
  )

  def test_comment_due_to_comma_between_prefix_and_command3(): Unit = checkTree(
    "//> , using dep org.foo:bar:42",
    """ScalaFile
      |  PsiComment(comment)('//> , using dep org.foo:bar:42')""".stripMargin
  )

  def test_comment_due_to_line_comment_prefix1(): Unit = checkTree(
    "// //> using dep",
    """ScalaFile
      |  PsiComment(comment)('// //> using dep')""".stripMargin
  )

  def test_comment_due_to_line_comment_prefix2(): Unit = checkTree(
    "///> using dep",
    """ScalaFile
      |  PsiComment(comment)('///> using dep')""".stripMargin
  )

  def test_invalid_due_to_comma_after_command(): Unit = checkTree(
    "//> using, dep org.foo:bar:42",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiErrorElement:Scala CLI key expected: option, dep, jar, etc.
      |      <empty list>
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_ERROR)(',')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.foo:bar:42')""".stripMargin
  )

  def test_invalid_due_to_missing_key(): Unit = checkTree(
    "//> using",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiErrorElement:Scala CLI key expected: option, dep, jar, etc.
      |      <empty list>""".stripMargin
  )

  def test_valid_even_though_there_is_a_comma_after_key(): Unit = checkTree(
    "//> using dep, org.foo:bar:42",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.foo:bar:42')""".stripMargin
  )

  def test_using_directive_without_value1(): Unit = checkTree(
    "//> using dep",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')""".stripMargin
  )

  def test_using_directive_without_value2(): Unit = checkTree(
    "//>  using dep",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace('  ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')""".stripMargin
  )

  def test_using_directive_without_value3(): Unit = checkTree(
    "//> using  dep",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace('  ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')""".stripMargin
  )

  def test_using_directive_with_value1(): Unit = checkTree(
    "//> using dep foo",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')""".stripMargin
  )

  def test_using_directive_with_value2(): Unit = checkTree(
    "//> using dep  foo",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace('  ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')""".stripMargin
  )

  def test_using_directive_with_space_separated_values1(): Unit = checkTree(
    "//> using dep foo bar",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_space_separated_values2(): Unit = checkTree(
    "//> using dep foo  bar",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace('  ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_separated_values1(): Unit = checkTree(
    "//> using dep foo,bar",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_separated_values2(): Unit = checkTree(
    "//> using dep foo, bar",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_separated_values3(): Unit = checkTree(
    "//> using dep foo ,bar",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_separated_values4(): Unit = checkTree(
    "//> using dep foo , bar",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_and_space_separated_values(): Unit = checkTree(
    "//> using dep foo,bar fizz",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('fizz')""".stripMargin
  )

  def test_do_not_bind_directive_to_class(): Unit = checkTree(
    "//> using dep foo\nclass Foo",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |  PsiWhiteSpace('\n')
      |  ScClass: Foo
      |    AnnotationsList
      |      <empty list>
      |    Modifiers
      |      <empty list>
      |    PsiElement(class)('class')
      |    PsiWhiteSpace(' ')
      |    PsiElement(identifier)('Foo')
      |    PrimaryConstructor
      |      AnnotationsList
      |        <empty list>
      |      Modifiers
      |        <empty list>
      |      Parameters
      |        <empty list>
      |    ExtendsBlock
      |      <empty list>""".stripMargin
  )

  def test_allow_leading_spaces(): Unit = checkTree(
    "  //> using foo",
    """ScalaFile
      |  PsiWhiteSpace('  ')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('foo')""".stripMargin
  )

  def test_allow_adjacent_prefix_and_command(): Unit = checkTree(
    "//>using foo",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('foo')""".stripMargin
  )
}
