package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaDocMarkdownFormatterTest extends AbstractScalaFormatterTestBase {
  override protected def version: ScalaVersion = LatestScalaVersions.Scala_3_5


  def test_unordered_list(): Unit = doTextTest(
    """
      |/**
      | * An list:
      | * - Item 1
      | * - Item 2
      | *   Let's do more
      | *   - oho ! sublist
      | *   - another one
      | * - Item 3
      | * Still part of 3
      | */
      |""".stripMargin,
    """
      |/**
      | * An list:
      | * - Item 1
      | * - Item 2
      | *   Let's do more
      | *   - oho ! sublist
      | *   - another one
      | * - Item 3
      | *   Still part of 3
      | */
      |""".stripMargin
  )

  def test_ordered_list(): Unit = doTextTest(
    """
      |/**
      | * An list:
      | * 1. Item 1
      | * 2. Item 2
      | *    Let's do more
      | *    1. oho ! sublist
      | *    2. another one
      | * Should be indented
      | * 3. Item 3
      | * Still part of 3
      | */
      |""".stripMargin,
    """
      |/**
      | * An list:
      | * 1. Item 1
      | * 2. Item 2
      | *    Let's do more
      | *    1. oho ! sublist
      | *    2. another one
      | *       Should be indented
      | * 3. Item 3
      | *    Still part of 3
      | */
      |""".stripMargin
  )

  def test_code_fence(): Unit = doTextTest(
    """
      |/**
      | * ```scala
      | * def test =
      | *   val x = 1 + 1
      | *   println(x)
      | *
      | *     object Test
      | * ```
      | *
      | *    ```
      | *      val x = 1 + 1
      | *    ```
      | */
      |""".stripMargin,
    // This formatting looks strange, but is basically what scaladoc does as well when parsing the code.
    """
      |/**
      | * ```scala
      | * def test =
      | *   val x = 1 + 1
      | *   println(x)
      | *
      | *     object Test
      | * ```
      | *
      | * ```
      | *      val x = 1 + 1
      | * ```
      | */
      |""".stripMargin
  )

  def test_header(): Unit = doTextTest(
    """
      |/**
      | *  # Header 1
      | *   ##  Header 2
      | *    ###   Header 3
      | */
      |""".stripMargin,
    """
      |/**
      | * # Header 1
      | * ##  Header 2
      | * ###   Header 3
      | */
      |""".stripMargin
  )


  def test_header_underlined(): Unit = doTextTest(
    """
      |/**
      | *  Header
      | *   ======
      | *
      | *    Header
      | *  ------
      | */
      |""".stripMargin,
    """
      |/**
      | * Header
      | * ======
      | *
      | * Header
      | * ------
      | */
      |""".stripMargin
  )

  def test_formats(): Unit = doTextTest(
    """
      |/**
      | *  *a test*
      | *   _another test_
      | *  **yet another test**
      | */
      |""".stripMargin,
    """
      |/**
      | * *a test*
      | * _another test_
      | * **yet another test**
      | */
      |""".stripMargin
  )

  def test_quotes(): Unit = doTextTest(
    """
      |/**
      | * > a
      | * >> b
      | * >>> c
      | * >
      | * > d
      | * >>> e
      | * >>
      | * >> f
      | */
      |""".stripMargin
  )

  def test_quotes_with_ws(): Unit = doTextTest(
    """
      |/**
      | * > a
      | * > > b
      | * > > > c
      | * >
      | * > d
      | * > > > e
      | * > >
      | * > > f
      | */
      |""".stripMargin
  )

  def test_quotes_with_two_ws(): Unit = doTextTest(
    """
      |/**
      | * > a
      | * >  > b
      | * >  >  > c
      | * >
      | * > d
      | * >  >  > e
      | * >  >
      | * >  > f
      | */
      |""".stripMargin
  )

  def test_quote_content(): Unit = doTextTest(
    """
      |/**
      | * >
      | * > > quoted quote
      | * >
      | * > *quoted italic*
      | * >
      | * > 1. aaa
      | * > 2. bbb
      | * >
      | * > - ccc
      | * > - ddd
      | * >
      | * > # Header
      | * > ## Header 2
      | * >
      | * > `code`
      | * > ``co`de``
      | * >
      | * > ```scala
      | * > val x = 1
      | * >
      | * > def test =
      | * >   println(x)
      | * > ```
      | */
      |""".stripMargin
  )

  def test_return_simple(): Unit = doTextTest(
    """
      |/**
      | *   @return    some text
      | *  some text on next line
      | */""".stripMargin,
    """
      |/**
      | * @return some text
      | *         some text on next line
      | */""".stripMargin
  )

  def test_return_all(): Unit = doTextTest(
    """
      |/**
      | * @return # Header
      | *      ## no header
      | *  some text
      | *
      | * more  text
      | */
      |""".stripMargin,
    """
      |/**
      | * @return # Header
      | *         ## no header
      | *         some text
      | *
      | *         more  text
      | */
      |""".stripMargin
  )

  def test_param_simple(): Unit = doTextTest(
    """
      |/**
      | * @param   p  some description
      | *           with multiple lines
      | */
      |""".stripMargin,
    """
      |/**
      | * @param p some description
      | *          with multiple lines
      | */
      |""".stripMargin
  )

  def test_note(): Unit = doTextTest(
    """
      |/**
      | * @note   some description
      | *           with multiple lines
      | *
      | *   other paragraph
      | */
      |""".stripMargin,
    """
      |/**
      | * @note some description
      | *       with multiple lines
      | *
      | *       other paragraph
      | */
      |""".stripMargin
  )

  // SCL-22730
  def test_scalaRef(): Unit = doTextTest(
    """
      |/**
      | * @param x time (from [[System.nanoTime()]])
      | * */
      |def f(x: Long): Unit = ()
      |""".stripMargin
  )
}
