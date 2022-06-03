package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

class Scala3FormatterCommentsTest extends Scala3FormatterBaseTest {

  private def withIndent(indent: String, indents: Int)(text: String): String =
    text.linesIterator.map(indent * indents + _).mkString("\n")

  private val comments = Seq(
    "// foo",
    "/* foo */",
    """// foo
      |// foo""".stripMargin,
    """/* foo */
      |/* foo */""".stripMargin,
    """/* foo
      |   foo */""".stripMargin,
  )
  private val docComments = Seq(
    "/** foo */",
    """/**
      | * foo
      | */""".stripMargin
  )
  private val contexts = Seq(
    ("", "", 0),
    ("{", "}", 1),
    ("class A {", "}", 1),
    ("object A:", "", 1),
    ("def a = {", "}", 1),
    ("def a =", "", 1)
  )
  private val bodies = Seq(
    "???",
    """???
      |???""".stripMargin
  )

  private def doCommentsFormatTest(text: String): Unit =
    for {
      (contextBefore, contextAfter, contextIndents) <- contexts
      body <- bodies
    } {
      val toContextIndent = withIndent("  ", contextIndents)(_)
      val toBodyIndent = withIndent("  ", contextIndents + 1)(_)
      val oldKeepFirstColumnCommentSetting: Boolean = getCommonSettings.KEEP_FIRST_COLUMN_COMMENT
      getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = false

      for {comment <- docComments ++ comments} {
        doTextTest(
          s"""$contextBefore
             |${toContextIndent(comment)}
             |${toContextIndent(text)}
             |${toBodyIndent(body)}
             |$contextAfter
             |""".stripMargin)
        doTextTest(
          s"""$contextBefore
             |$comment
             |${toContextIndent(text)}
             |${toBodyIndent(body)}
             |$contextAfter
             |""".stripMargin,
          s"""$contextBefore
             |${toContextIndent(comment)}
             |${toContextIndent(text)}
             |${toBodyIndent(body)}
             |$contextAfter
             |""".stripMargin
        )
      }

      for {comment <- comments} {
        getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = false
        doTextTest(
          s"""$contextBefore
             |${toContextIndent(text)}
             |${toBodyIndent(comment)}
             |${toBodyIndent(body)}
             |$contextAfter
             |""".stripMargin)
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
        getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = true
        doTextTest(
          s"""$contextBefore
             |${toContextIndent(text)}
             |$comment
             |${toBodyIndent(body)}
             |$contextAfter
             |""".stripMargin)
      }

      getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = oldKeepFirstColumnCommentSetting
    }

  // SCL-20166
  def testExtension(): Unit = doCommentsFormatTest("extension (c: Circle)")

  // TODO ignored
  def _testWith(): Unit = doCommentsFormatTest("class A extends B with")

  // TODO ignored
  def _testClass(): Unit = doCommentsFormatTest("class A:")

  // TODO ignored
  def _testClassWithParameter(): Unit = doCommentsFormatTest("class C(x: Int):")

  // TODO ignored
  def _testClassExtends(): Unit = doCommentsFormatTest("class A extends B:")

  // TODO ignored
  def _testObject(): Unit = doCommentsFormatTest("object A:")

  // TODO ignored
  def _testEnum(): Unit = doCommentsFormatTest("enum Color:")

  // TODO ignored
  def _testNew(): Unit = doCommentsFormatTest("new A:")

  // TODO ignored
  def _testPackage(): Unit = doCommentsFormatTest("package p:")

  // TODO ignored
  def _testDef(): Unit = doCommentsFormatTest("def x =")

  // TODO ignored
  def _testVal(): Unit = doCommentsFormatTest("val x =")

  // TODO ignored
  def _testVar(): Unit = doCommentsFormatTest("var x =")

  // TODO ignored
  def _testLambda(): Unit = doCommentsFormatTest("var x = (a: Int) =>")

  // TODO ignored
  def _testContextFunction(): Unit = doCommentsFormatTest("type Executable[T] = ExecutionContext ?=>")

  // TODO ignored
  def _testGenerator(): Unit = doCommentsFormatTest("x <-")

  // TODO ignored
  def _testTry(): Unit = doCommentsFormatTest("try")

  // TODO ignored
  def _testCatch(): Unit = doCommentsFormatTest(
    """try
      |  ???
      |catch""".stripMargin)

  // TODO ignored
  def _testFinally(): Unit = doCommentsFormatTest(
    """try
      |  ???
      |finally""".stripMargin)

  // TODO ignored
  def _testWhile(): Unit = doCommentsFormatTest("while")

  // TODO ignored
  def _testWhileDo(): Unit = doCommentsFormatTest("while true do")

  // TODO ignored
  def _testWhileParens(): Unit = doCommentsFormatTest("while (true)")

  // TODO ignored
  def _testFor(): Unit = doCommentsFormatTest("for")

  // TODO ignored
  def _testForParens(): Unit = doCommentsFormatTest("for (x <- Seq(1,2))")

  // TODO ignored
  def _testForBraces(): Unit = doCommentsFormatTest("for {x <- Seq(1,2)}")

  // TODO ignored
  def _testForDo(): Unit = doCommentsFormatTest("for x <- Seq(1,2) do")

  // TODO ignored
  def _testForYield(): Unit = doCommentsFormatTest("for x <- Seq(1,2) yield")

  // TODO ignored
  def _testMatch(): Unit = doCommentsFormatTest("??? match")

  // TODO ignored
  def _testReturn(): Unit = doCommentsFormatTest("return")

  // TODO ignored
  def _testThrow(): Unit = doCommentsFormatTest("throw")

  // TODO ignored
  def _testIf(): Unit = doCommentsFormatTest("if")

  // TODO ignored
  def _testIfThen(): Unit = doCommentsFormatTest("if ??? then")

  // TODO ignored
  def _testIfParens(): Unit = doCommentsFormatTest("if (???)")

  // TODO ignored
  def _testElseIf(): Unit = doCommentsFormatTest(
    """if false then
      |  ???
      |else if""".stripMargin)

  // TODO ignored
  def _testElseIfThen(): Unit = doCommentsFormatTest(
    """if false then
      |  ???
      |else if true then""".stripMargin)

  // TODO ignored
  def _testElseIfParens(): Unit = doCommentsFormatTest(
    """if false then
      |  ???
      |else if (true)""".stripMargin)

  // TODO ignored
  def _testElse(): Unit = doCommentsFormatTest(
    """if false then
      |  ???
      |else""".stripMargin)
}
