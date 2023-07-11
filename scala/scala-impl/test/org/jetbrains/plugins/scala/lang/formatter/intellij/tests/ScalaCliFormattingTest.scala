package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaCliFormattingTest extends AbstractScalaFormatterTestBase {

  def test_format_scala_cli_directive_sub_elements(): Unit = doTextTest(
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

  def test_add_empty_line_between_directive_and_other_scala1(): Unit = doTextTest(
    """//> using dep foo
      |class Foo
      |""".stripMargin,
    """//> using dep foo
      |
      |class Foo
      |""".stripMargin
  )

  def test_add_empty_line_between_directive_and_other_scala2(): Unit = doTextTest(
    """//> using dep foo
      |// foo
      |class Foo
      |""".stripMargin,
    """//> using dep foo
      |
      |// foo
      |class Foo
      |""".stripMargin
  )
}
