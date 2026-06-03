package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

class ScalaCommentsInBlockFormatterKeepFirstColumnTest extends ScalaCommentsInBlockFormatterTestBase(true)

class ScalaCommentsInBlockFormatterDoNotKeepFirstColumnTest extends ScalaCommentsInBlockFormatterTestBase(false)

//The test was revived (it was removed in commit c71e1e6d and now it's reverted back)
abstract class ScalaCommentsInBlockFormatterTestBase(
  keepFirstColumnComment: Boolean
) extends Scala3FormatterBaseTest {

  override protected def setUp(): Unit = {
    super.setUp()
    getCommonSettings.KEEP_FIRST_COLUMN_COMMENT = keepFirstColumnComment
  }

  private def withIndent(indent: String, indents: Int)(text: String): String =
    text.linesIterator.map(indent * indents + _).mkString("\n")

  private val comments = Seq(
    "//line comment",
    """//line comment 1
      |//line comment 2""".stripMargin,
    "/* block comment */",
    """/* block comment 1 */
      |/* block comment 2 */""".stripMargin,
    """/* block comment line 1
      |   block comment line 2 */""".stripMargin,
  )
  private val docComments = Seq(
    "\n/** scaladoc comment */",
    """
      |/**
      | * scaladoc comment
      | */""".stripMargin
  )
  private val EmptyContext: (String, String, Int) = ("", "", 0)
  private val allExpressionContexts: Seq[(String, String, Int)] = Seq(
    EmptyContext,
    ("{", "}", 1),
    ("class A {", "}", 1),
    ("object A:", "", 1),
    ("def a = {", "}", 1),
    ("def a =", "", 1)
  )
  private val expressionBodies = Seq(
    "???",
    """???
      |???""".stripMargin
  )

  private def doCommentsFormatTest(
    text: String,
    bodies: Seq[String] = expressionBodies,
    contexts: Seq[(String, String, Int)] = allExpressionContexts,
    extraBodyIndent: Int = 0
  ): Unit = {
    val contextsApplicableForSettings =
      if (keepFirstColumnComment)
        contexts.filter(_._3 == 0)
      else
        contexts
    for {
      (contextBefore, contextAfter, contextIndents) <- contextsApplicableForSettings
      body <- bodies
    } {
      val toContextIndent = withIndent("  ", contextIndents)(_)
      val toBodyIndent = withIndent("  ", contextIndents + 1 + extraBodyIndent)(_)

      if (!keepFirstColumnComment) {
        for {comment <- docComments ++ comments} {
          val before =
            s"""$contextBefore
               |$comment
               |${toContextIndent(text)}
               |${toBodyIndent(body)}
               |$contextAfter
               |""".stripMargin
          val after =
            s"""$contextBefore
               |${toContextIndent(comment)}
               |${toContextIndent(text)}
               |${toBodyIndent(body)}
               |$contextAfter
               |""".stripMargin

          doTextTest(before, after)
          doTextTest(after)
        }
      }

      for {comment <- comments} {
        val before =
          s"""$contextBefore
             |${toContextIndent(text)}
             |$comment
             |${toBodyIndent(body)}
             |$contextAfter
             |""".stripMargin
        val after =
          s"""$contextBefore
             |${toContextIndent(text)}
             |${toBodyIndent(comment)}
             |${toBodyIndent(body)}
             |$contextAfter
             |""".stripMargin

        if (keepFirstColumnComment) {
          doTextTest(before)
        }
        else {
          doTextTest(after)
          doTextTest(before, after)
        }
      }
    }
  }

  // SCL-20166
  def testExtension(): Unit = doCommentsFormatTest("extension (c: Circle)")

  def testClass(): Unit = doCommentsFormatTest("class A:")

  def testClassWithParameter(): Unit = doCommentsFormatTest("class C(x: Int):")

  def testClassExtends(): Unit = doCommentsFormatTest("class A extends B:")

  def testObject(): Unit = doCommentsFormatTest("object A:")

  def testEnum(): Unit = doCommentsFormatTest("enum Color:")

  def testNew(): Unit = doCommentsFormatTest("new A:")

  def testPackage(): Unit = doCommentsFormatTest("package p:", contexts = Seq(EmptyContext))

  def testDef(): Unit = doCommentsFormatTest("def x =")

  def testVal(): Unit = doCommentsFormatTest("val x =")

  def testVar(): Unit = doCommentsFormatTest("var x =")

  def testLambda(): Unit = doCommentsFormatTest("var x = (a: Int) =>")

  def testContextFunction(): Unit = doCommentsFormatTest("type Executable[T] = ExecutionContext ?=>", bodies = Seq("String"))

  def testTry(): Unit = doCommentsFormatTest("try")

  def testCatch(): Unit = doCommentsFormatTest(
    """try
      |  ???
      |catch""".stripMargin)

  def testFinally(): Unit = doCommentsFormatTest(
    """try
      |  ???
      |finally""".stripMargin)

  def testWhile(): Unit = doCommentsFormatTest("while")

  def testWhileDo(): Unit = doCommentsFormatTest(
    """while
      |  true
      |do""".stripMargin)

  def testWhileDo_1(): Unit = doCommentsFormatTest("while true do")

  def testWhileParens(): Unit = doCommentsFormatTest("while (true)")

  def testForParens(): Unit = doCommentsFormatTest("for (x <- Seq(1, 2))")

  def testForBraces(): Unit = doCommentsFormatTest("for {x <- Seq(1, 2)}")

  def testForDo(): Unit = doCommentsFormatTest("for x <- Seq(1, 2) do")

  def testForYield(): Unit = doCommentsFormatTest("for x <- Seq(1, 2) yield")

  def testMatch(): Unit = doCommentsFormatTest(
    """??? match
      |  case _ => """.stripMargin,
    extraBodyIndent = 1
  )

  def testReturn(): Unit = doCommentsFormatTest("return")

  def testThrow(): Unit = doCommentsFormatTest("throw")

  def testIf(): Unit = doCommentsFormatTest("if")

  def testIfThen(): Unit = doCommentsFormatTest("if ??? then")

  def testIfParens(): Unit = doCommentsFormatTest("if (???)")

  def testElseIf(): Unit = doCommentsFormatTest(
    """if false then
      |  ???
      |else if""".stripMargin)

  def testElseIfThen(): Unit = doCommentsFormatTest(
    """if false then
      |  ???
      |else if true then""".stripMargin)

  def testElseIfParens(): Unit = doCommentsFormatTest(
    """if false then
      |  ???
      |else if (true)""".stripMargin)

  def testElse(): Unit = doCommentsFormatTest(
    """if false then
      |  ???
      |else""".stripMargin)
}
