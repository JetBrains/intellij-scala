package org.jetbrains.plugins.scala
package lang
package actions
package editor

import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class AutoBracesTest extends EditorActionTestBase {
  val space = " "
  val indent = "  "


//  def testBlubBlub(): Unit = checkGeneratedTextAfterTyping(
//    s"""
//       |for (e <- expr)
//       |  $CARET
//       |  expr
//       |""".stripMargin,
//    s"""
//       |for (e <- expr) {
//       |  e$CARET
//       |  expr
//       |}
//       |""".stripMargin,
//    'e'
//  )


  def testEnterAfterExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |$CARET
       |""".stripMargin -> NextConstructOnNewline,
    '\n',
    checkContextsWithPostfix = false
  )

  def testEnterAfterExprAndIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |$indent
       |$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |$indent
       |$CARET
       |""".stripMargin -> NextConstructOnNewline,
    '\n',
    checkContextsWithPostfix = false
  )

  def testTypingAfterIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test = {
       |  expr
       |  e$CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  expr
       |  e$CARET
       |""".stripMargin -> NextConstructOnNewline,
    'e'
  )

  def testEnterBeforeIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test = $CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =$space
       |  $CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =$space
       |  $CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    '\n'
  )

  def testTypingAfterIndentBeforeIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  $CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test = {
       |  e$CARET
       |  expr
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  e$CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    'e'
  )

  def testTypingAfterDoubleIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |   e$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |   e$CARET
       |""".stripMargin -> NextConstructOnNewline,
    'e'
  )

  def testTypingAfterSecondIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   .prod
       |  $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test = {
       |  expr
       |   .prod
       |  e$CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  expr
       |   .prod
       |  e$CARET
       |""".stripMargin -> NextConstructOnNewline,
    'e'
  )

  def testEnterAfterSecondIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   + expr
       |  $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |   + expr
       |$indent
       |$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |   + expr
       |$indent
       |$CARET
       |""".stripMargin -> NextConstructOnNewline,
    '\n'
  )

  def testTypingInUnindented(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |e$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |e$CARET
       |""".stripMargin -> NextConstructOnNewline,
    'e'
  )

  def testTypingBetweenCommentAndIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  // test
       |  $CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test = {
       |  // test
       |  e$CARET
       |  expr
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  // test
       |  e$CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    'e'
  )


  def testEnterInsideOfIndentedCall(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  call($CARET)
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  call(
       |    $CARET
       |  )
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  call(
       |    $CARET
       |  )
       |""".stripMargin -> NextConstructOnNewline,
    '\n'
  )


  def testTypingInsideOfIndentedCall(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  call(
       |  $CARET
       |  )
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  call(
       |  e$CARET
       |  )
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  call(
       |  e$CARET
       |  )
       |""".stripMargin -> NextConstructOnNewline,
    'e'
  )

  def testDeletingLastExprBefore(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  e$CARET
       |  expr
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  $CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test = {
       |  $CARET
       |  expr
       |}
       |""".stripMargin -> NextConstructOnSameLine,
  )

  def testDeletingLastExprAfter(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  expr
       |  e$CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test = {
       |  expr
       |  $CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
  )

  def testDeletingLastExprWithComment(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  // comment
       |  expr
       |  e$CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test = {
       |  // comment
       |  expr
       |  $CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test = {
       |  // comment
       |  expr
       |  $CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
  )

  def testDeletingLastExprWithStatement(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  val x = expr
       |  x$CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test = {
       |  val x = expr
       |  $CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test = {
       |  val x = expr
       |  $CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
  )

  /**************************************** Test in multiple contexts *************************************************/
  case class SubsequentConstructNewlineSeparator(separator: String)
  val NextConstructOnNewline = SubsequentConstructNewlineSeparator("\n")
  val NextConstructOnSameLine = SubsequentConstructNewlineSeparator(" ")

  val contexts = Seq(
    """
      |def test =
      |""".stripMargin -> "",
    """
      |val test =
      |""".stripMargin -> "",
    """
      |if (cond)
      |""".stripMargin -> "",
    """
      |if (cond)
      |""".stripMargin -> "else elseBranch",
    """
      |if (cond) thenBranch
      |else
      |""".stripMargin -> "",
    """
      |for (x <- xs)
      |""".stripMargin -> "",
    """
      |for (x <- xs) yield
      |""".stripMargin -> "",
    """
      |while (cond)
      |""".stripMargin -> "",
    """
      |try something
      |finally
      |""".stripMargin -> "",
    """
      |try
      |""".stripMargin -> "catch { e => something }",
    """
      |try
      |""".stripMargin -> "finally something",
  )

  def checkBackspaceInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                                  bodyAfter: (String, SubsequentConstructNewlineSeparator),
                                  bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                                  checkContextsWithPostfix: Boolean = true): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, checkContextsWithPostfix)(checkGeneratedTextAfterBackspace)

  def checkTypingInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                               bodyAfter: (String, SubsequentConstructNewlineSeparator),
                               bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                               typedChar: Char,
                               checkContextsWithPostfix: Boolean = true): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, checkContextsWithPostfix)(checkGeneratedTextAfterTyping(_, _, typedChar))

  def checkInAllContexts(bodyBefore: (String, SubsequentConstructNewlineSeparator),
                         bodyAfter: (String, SubsequentConstructNewlineSeparator),
                         bodyAfterWithSettingsTurnedOff: (String, SubsequentConstructNewlineSeparator),
                         checkContextsWithPostfix: Boolean = true)
                        (check: (String, String) => Unit): Unit = {

    def transform(body: String): String =
      body.trim.replace("def test =", "")

    val settings = ScalaApplicationSettings.getInstance()

    for ((context, contextPostfix) <- contexts if checkContextsWithPostfix || contextPostfix.isEmpty) {
      def buildBody(body: (String, SubsequentConstructNewlineSeparator)): String = {
        val (text, sep) = body

        val postfix = {
          if (contextPostfix.isEmpty) "\n"
          else sep.separator + contextPostfix
        }

        context.trim + transform(text) + postfix
      }

      val before = buildBody(bodyBefore)
      assert(settings.HANDLE_BLOCK_BRACES_AUTOMATICALLY)

      try {
        check(before, buildBody(bodyAfter))
        settings.HANDLE_BLOCK_BRACES_AUTOMATICALLY = false
        check(before, buildBody(bodyAfterWithSettingsTurnedOff))
      } finally {
        settings.HANDLE_BLOCK_BRACES_AUTOMATICALLY = true
      }
    }
  }
}