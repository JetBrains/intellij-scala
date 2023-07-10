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

  def test_key_in_backticks(): Unit = checkTree(
    "//> using `foo`",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('`foo`')""".stripMargin
  )

  def test_value_in_backticks(): Unit = checkTree(
    "//> using foo `bar`",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('foo')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('`bar`')""".stripMargin
  )

  def test_value_in_double_quotes(): Unit = checkTree(
    """//> using foo "bar"""",
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('foo')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('"bar"')""".stripMargin
  )

  // See https://scala-cli.virtuslab.org/docs/guides/using-directives.
  // The examples of the directives were copied literally, but the comments were
  // removed, because at the time of writing our Scala CLI parser does not deal
  // with trailing comments yet. See SCL-
  def test_examples_from_scala_cli_website(): Unit = checkTree(
    """//> using foo bar baz
      |//> using scala 2.13
      |//> using platform scala-js
      |//> using options -Xasync
      |//> using dep org::name:version
      |//> using dep org:name:version
      |//> using dep org::name:version,url=url
      |//> using resourceDir dir
      |//> using javaOpt opt
      |//> using target.scope test
      |//> using testFramework framework
      |//> using options -coverage-out:${.}
      |//> using options -coverage-out:$${.}
      |//> using dep "n1o::lib:123"
      |//> using test.dep org.scalameta::munit::0.7.29
      |//> using dep org.scalameta::munit::0.7.29
      |//> using test.dep org.scalameta::munit::0.7.29
      |//> using test.jar path/to/dep.jar
      |//> using test.sourceJar path/to/some-sources.jar
      |//> using test.javaOpt -Dfoo=bar
      |//> using test.javacOpt source 1.8 target 1.8
      |//> using test.javaProp foo1=bar1
      |//> using test.option -Xfatal-warnings
      |//> using test.resourceDir testResources
      |//> using test.toolkit latest""".stripMargin,
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('foo')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('bar')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('baz')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('scala')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('2.13')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('platform')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('scala-js')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('options')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('-Xasync')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org::name:version')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org:name:version')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org::name:version')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMA)(',')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('url=url')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('resourceDir')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('dir')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('javaOpt')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('opt')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('target.scope')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('test')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('testFramework')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('framework')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('options')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('-coverage-out:${.}')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('options')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('-coverage-out:$${.}')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('"n1o::lib:123"')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.scalameta::munit::0.7.29')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.scalameta::munit::0.7.29')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('org.scalameta::munit::0.7.29')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.jar')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('path/to/dep.jar')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.sourceJar')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('path/to/some-sources.jar')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.javaOpt')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('-Dfoo=bar')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.javacOpt')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('source')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('1.8')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('target')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('1.8')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.javaProp')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo1=bar1')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.option')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('-Xfatal-warnings')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.resourceDir')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('testResources')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('test.toolkit')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('latest')""".stripMargin
  )

  def test_trailing_comment(): Unit = checkTree(
    """//> using dep // foo
      |//> using dep foo // bar
      |""".stripMargin,
    """ScalaFile
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// foo')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_CLI_DIRECTIVE)
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScCliDirectiveToken(tCLI_DIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// bar')
      |  PsiWhiteSpace('\n')""".stripMargin
  )
}
