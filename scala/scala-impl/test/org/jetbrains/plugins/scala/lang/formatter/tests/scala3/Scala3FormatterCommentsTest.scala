package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

class Scala3FormatterCommentsTest extends Scala3FormatterBaseTest {

  private def withIndent(indent: String, indents: Int)(text: String): String =
    text.linesIterator.map(indent * indents + _).mkString("\n")

  private val allComments = Seq(
    "// foo",
    "/* foo */",
    "/** foo */",
    """// foo
      |// foo""".stripMargin,
    """/* foo */
      |/* foo */""".stripMargin,
    """/* foo
      |   foo */""".stripMargin,
    """/**
      |  * foo
      |  */""".stripMargin
  )
  private val allContexts = Seq(
    ("class A {", "}", 1),
    ("class A:", "", 1),
    ("object A {", "}", 1),
    ("object A:", "", 1),
    ("def a =", "", 1),
  )
  private val allIndents = Seq("  ", "    ", "\t")
  private val allBodies = Seq(
    "???",
    """???
      |???""".stripMargin
  )

  private def doCommentsFormatTest(text: String): Unit =
    for {
      comment <- allComments
      (contextBefore, contextAfter, contextIndents) <- allContexts
      indent <- allIndents
      body <- allBodies
    } {
      val toContextIndent = withIndent(indent, contextIndents)(_)
      val toBodyIndent = withIndent(indent, contextIndents + 1)(_)
      val oldKeepFirstColumnCommentSetting: Boolean = getSettings.KEEP_FIRST_COLUMN_COMMENT
      doTextTest(
        s"""$contextBefore
           |${toContextIndent(comment)}
           |${toContextIndent(text)}
           |${toBodyIndent(body)}
           |$contextAfter
           |""".stripMargin)
      doTextTest(
        s"""$contextBefore
           |${toContextIndent(text)}
           |${toBodyIndent(comment)}
           |${toBodyIndent(body)}
           |$contextAfter
           |""".stripMargin)
      getSettings.KEEP_FIRST_COLUMN_COMMENT = false
      doTextTest(
        s"""$contextBefore
           |${toContextIndent(text)}
           |$comment
           |${toBodyIndent(body)}
           |$contextAfter
           |""".stripMargin,
        s"""$contextBefore
           |${toContextIndent(text)}
           |${toBodyIndent(comment)}
           |${toBodyIndent(body)}
           |$contextAfter
           |""".stripMargin
      )
      getSettings.KEEP_FIRST_COLUMN_COMMENT = true
      doTextTest(
        s"""$contextBefore
           |${toContextIndent(text)}
           |$comment
           |${toBodyIndent(body)}
           |$contextAfter
           |""".stripMargin)
      getSettings.KEEP_FIRST_COLUMN_COMMENT = oldKeepFirstColumnCommentSetting
    }

  def testAssign(): Unit = doCommentsFormatTest("def x =")

  // SCL-20166
  def testExtension(): Unit = doCommentsFormatTest("extension (c: Circle)")
}
