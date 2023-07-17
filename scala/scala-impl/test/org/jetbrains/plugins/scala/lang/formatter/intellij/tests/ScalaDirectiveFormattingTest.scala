package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaDirectiveFormattingTest extends AbstractScalaFormatterTestBase {

  def test_format_scala_directive_sub_elements(): Unit = doTextTest(
    """//> using  dep  foo  bar
      |//> using  dep , foo , bar
      |""".stripMargin,
    """//> using dep foo bar
      |//> using dep, foo, bar
      |""".stripMargin
  )

  def test_do_not_remove_empty_lines_between_directives(): Unit = doTextTest(
    """//> using dep foo
      |
      |//> using dep foo
      |""".stripMargin
  )

  def test_do_not_add_empty_line_between_directive_and_other_scala1(): Unit = doTextTest(
    """//> using dep foo
      |class Foo
      |""".stripMargin
  )

  def test_do_not_add_empty_line_between_directive_and_other_scala2(): Unit = doTextTest(
    """//> using dep foo
      |// foo
      |class Foo
      |""".stripMargin
  )

  def test_do_not_add_empty_line_between_package_declaration_and_directive(): Unit = doTextTest(
    """package foo
      |//> using dep""".stripMargin
  )

  def test_do_not_add_empty_line_between_directive_and_package_declaration(): Unit = doTextTest(
    """//> using dep
      |package foo""".stripMargin
  )

  def test_do_not_add_empty_line_when_directive_surrounded_by_package_declarations(): Unit = doTextTest(
    """package foo
      |//> using dep
      |package bar""".stripMargin
  )

  def test_do_not_add_empty_line_between_class_declaration_and_directive(): Unit = doTextTest(
    """class Foo
      |//> using dep""".stripMargin
  )

  def test_do_not_add_empty_line_between_directive_and_class_declaration(): Unit = doTextTest(
    """//> using dep
      |class Foo""".stripMargin
  )

  def test_do_not_add_empty_line_when_directive_surrounded_by_class_declarations(): Unit = doTextTest(
    """class Foo
      |//> using dep
      |class Bar""".stripMargin
  )

  def test_indent_but_do_not_add_empty_line_when_directive_is_placed_in_class_body(): Unit = doTextTest(
    """class Foo {
      |//> using dep
      |}""".stripMargin,
    """class Foo {
      |  //> using dep
      |}""".stripMargin,
  )

  def test_indent_but_do_not_add_empty_line_when_directive_is_placed_in_nested_class_body(): Unit = doTextTest(
    """class Foo {
      |  class Bar {
      |//> using dep
      |  }
      |}""".stripMargin,
    """class Foo {
      |  class Bar {
      |    //> using dep
      |  }
      |}""".stripMargin,
  )

  def test_surrounded_by_comments(): Unit = doTextTest(
    """// Foo
      |//> using dep
      |// Foo
      |""".stripMargin
  )

  def test_surrounded_by_newlines_and_comments(): Unit = doTextTest(
    """// Foo
      |
      |//> using dep
      |
      |// Foo
      |""".stripMargin
  )
}
