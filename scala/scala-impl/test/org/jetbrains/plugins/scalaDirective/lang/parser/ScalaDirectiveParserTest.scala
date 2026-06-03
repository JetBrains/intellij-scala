package org.jetbrains.plugins.scalaDirective.lang.parser

import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase

class ScalaDirectiveParserTest extends SimpleScalaParserTestBase {

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
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiErrorElement:Scala directive key expected: option, dep, jar, etc.
      |      <empty list>
      |    ScDirectiveToken(tDIRECTIVE_ERROR)(',')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('org.foo:bar:42')""".stripMargin
  )

  def test_invalid_due_to_missing_key(): Unit = checkTree(
    "//> using",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiErrorElement:Scala directive key expected: option, dep, jar, etc.
      |      <empty list>""".stripMargin
  )

  def test_valid_even_though_there_is_a_comma_after_key(): Unit = checkTree(
    "//> using dep, org.foo:bar:42",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    ScDirectiveToken(tDIRECTIVE_COMMA)(',')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('org.foo:bar:42')""".stripMargin
  )

  def test_using_directive_without_value1(): Unit = checkTree(
    "//> using dep",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')""".stripMargin
  )

  def test_using_directive_without_value2(): Unit = checkTree(
    "//>  using dep",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace('  ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')""".stripMargin
  )

  def test_using_directive_without_value3(): Unit = checkTree(
    "//> using  dep",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace('  ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')""".stripMargin
  )

  def test_using_directive_with_value1(): Unit = checkTree(
    "//> using dep foo",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')""".stripMargin
  )

  def test_using_directive_with_value2(): Unit = checkTree(
    "//> using dep  foo",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace('  ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')""".stripMargin
  )

  def test_using_directive_with_space_separated_values1(): Unit = checkTree(
    "//> using dep foo bar",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_space_separated_values2(): Unit = checkTree(
    "//> using dep foo  bar",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace('  ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_separated_values1(): Unit = checkTree(
    "//> using dep foo,bar",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
      |    ScDirectiveToken(tDIRECTIVE_COMMA)(',')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_separated_values2(): Unit = checkTree(
    "//> using dep foo, bar",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
      |    ScDirectiveToken(tDIRECTIVE_COMMA)(',')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_separated_values3(): Unit = checkTree(
    "//> using dep foo ,bar",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMA)(',')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_separated_values4(): Unit = checkTree(
    "//> using dep foo , bar",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMA)(',')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('bar')""".stripMargin
  )

  def test_using_directive_with_comma_and_space_separated_values(): Unit = checkTree(
    "//> using dep foo,bar fizz",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
      |    ScDirectiveToken(tDIRECTIVE_COMMA)(',')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('bar')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('fizz')""".stripMargin
  )

  def test_do_not_bind_directive_to_class(): Unit = checkTree(
    "//> using dep foo\nclass Foo",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
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
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('foo')""".stripMargin
  )

  def test_allow_adjacent_prefix_and_command(): Unit = checkTree(
    "//>using foo",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('foo')""".stripMargin
  )

  def test_key_in_backticks(): Unit = checkTree(
    "//> using `foo`",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('`foo`')""".stripMargin
  )

  def test_value_in_backticks(): Unit = checkTree(
    "//> using foo `bar`",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('foo')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('`bar`')""".stripMargin
  )

  def test_value_in_double_quotes(): Unit = checkTree(
    """//> using foo "bar"""",
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('foo')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('"bar"')""".stripMargin
  )

  def test_examples_from_scala_cli_website(): Unit = checkTree(
    """//> using foo bar baz
      |//> using scala 2.13
      |//> using platform scala-js
      |//> using options -Xasync
      |//> using dep org::name:version // defines dependency to a given library more in dedicated guide
      |//> using dep org:name:version // defines dependency to a given java library, note the : instead of ::
      |//> using dep org::name:version,url=url // defines dependency to a given library with a fallback to its jar url
      |//> using resourceDir dir // marks directory as source of resources. Resources accessible at runtime and packaged together with compiled code.
      |//> using javaOpt opt // use given java options when running application or tests
      |//> using target.scope test // used to marked or unmarked given source as test
      |//> using testFramework framework // select test framework to use
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
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('foo')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('bar')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('baz')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('scala')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('2.13')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('platform')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('scala-js')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('options')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('-Xasync')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('org::name:version')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// defines dependency to a given library more in dedicated guide')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('org:name:version')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// defines dependency to a given java library, note the : instead of ::')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('org::name:version')
      |    ScDirectiveToken(tDIRECTIVE_COMMA)(',')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('url=url')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// defines dependency to a given library with a fallback to its jar url')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('resourceDir')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('dir')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// marks directory as source of resources. Resources accessible at runtime and packaged together with compiled code.')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('javaOpt')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('opt')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// use given java options when running application or tests')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('target.scope')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('test')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// used to marked or unmarked given source as test')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('testFramework')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('framework')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// select test framework to use')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('options')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('-coverage-out:${.}')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('options')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('-coverage-out:$${.}')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('"n1o::lib:123"')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('org.scalameta::munit::0.7.29')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('org.scalameta::munit::0.7.29')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('org.scalameta::munit::0.7.29')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.jar')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('path/to/dep.jar')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.sourceJar')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('path/to/some-sources.jar')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.javaOpt')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('-Dfoo=bar')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.javacOpt')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('source')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('1.8')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('target')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('1.8')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.javaProp')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo1=bar1')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.option')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('-Xfatal-warnings')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.resourceDir')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('testResources')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('test.toolkit')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('latest')""".stripMargin
  )

  def test_trailing_comment(): Unit = checkTree(
    """//> using dep // foo
      |//> using dep //foo
      |//> using dep foo // bar
      |//> using //foo
      |""".stripMargin,
    """ScalaFile
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// foo')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('//foo')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_KEY)('dep')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_VALUE)('foo')
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('// bar')
      |  PsiWhiteSpace('\n')
      |  PsiElement(SCALA_DIRECTIVE)
      |    ScDirectiveToken(tDIRECTIVE_PREFIX)('//>')
      |    PsiWhiteSpace(' ')
      |    ScDirectiveToken(tDIRECTIVE_COMMAND)('using')
      |    PsiErrorElement:Scala directive key expected: option, dep, jar, etc.
      |      <empty list>
      |    PsiWhiteSpace(' ')
      |    PsiComment(comment)('//foo')
      |  PsiWhiteSpace('\n')""".stripMargin
  )
}
