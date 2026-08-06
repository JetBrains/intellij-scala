package org.jetbrains.plugins.scala.lang.randomTyping

import org.jetbrains.plugins.scala.{RandomTypingTests, ScalaVersion}
import org.junit.experimental.categories.Category

import scala.util.Random

@Category(Array(classOf[RandomTypingTests]))
class RandomScalaDocTypingTest extends RandomTypingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  override def logging: Boolean = true

  def test_baseText(): Unit =
    doTest(baseText)

  def test_quoted(): Unit = doTest(
    prefixLines("> ")(baseText)
  )

  def test_double_quoted_with_space(): Unit = doTest(
    prefixLines("> > ")(baseText),
  )

  def test_double_quoted_without_space(): Unit = doTest(
    prefixLines(">> ")(baseText),
  )

  def test_in_list(): Unit = doTest(
    prefixLines("   ", "1. ")(baseText),
  )

  def test_in_return(): Unit = doTest(
    s"""@return First line
       |${prefixLines("        ")(baseText)}
       |""".stripMargin,
  )

  def test_in_param(): Unit = doTest(
    s"""@param p First line
       |${prefixLines("         ")(baseText)}
       |""".stripMargin,
  )

  private def doTest(docText: String, seed: Int = new Random().nextInt()): Unit =
    typeRandomly(toComment(docText), seed)

  private def toComment(docText: String): String =
    s"""
      |/**
      |${prefixLines(" * ")(docText)}
      | */
      |object Test
      |""".stripMargin

  lazy val baseText: String =
    """
      |*quoted italic*
      |another line that belongs to the one above
      |
      |new paragraph
      |with two lines
      |
      |> a quoted start
      |> of a paragraph
      |>> with nested
      |>> paragraph
      |
      |1. aaa
      |   a2a2
      |2. bbb
      |   paragraph
      |   3. bxbxbx
      |      bx2bx2
      |
      |- ccc
      |  c2c2
      |- ddd
      |  paragraph
      |   3. dxdxdx
      |      dx2dx2
      |
      |# Header 1
      |## Header 2
      |### Header 3
      |#### Header 4
      |##### Header 5
      |###### Header 6
      |
      |`code`
      |``co`de``
      |
      |```scala
      |val x = 1
      |
      |def test =
      |  println(x)
      |```
      |
      |{{{
      |val x = 1
      |
      |def test =
      |  println(x)
      |}}}
      |""".stripMargin.trim

  def prefixLines(prefix: String, firstLinePrefix: String = null)(code: String): String = {
    var first = true
    code.linesIterator
      .map {
        case line if firstLinePrefix != null && first =>
          first = false
          firstLinePrefix + line
        case line =>
          prefix + line
      }
      .mkString("\n")
  }
}
