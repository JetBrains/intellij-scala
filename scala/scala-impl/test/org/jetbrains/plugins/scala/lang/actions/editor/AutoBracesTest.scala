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
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |$CARET
       |""".stripMargin,
    '\n'
  )

  def testEnterAfterExprAndIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |$indent
       |$CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |$indent
       |$CARET
       |""".stripMargin,
    '\n'
  )

  def testTypingAfterIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin,
    s"""
       |def test = {
       |  expr
       |  e$CARET
       |}
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |  e$CARET
       |""".stripMargin,
    'e'
  )

  def testEnterBeforeIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test = $CARET
       |  expr
       |""".stripMargin,
    s"""
       |def test =$space
       |  $CARET
       |  expr
       |""".stripMargin,
    s"""
       |def test =$space
       |  $CARET
       |  expr
       |""".stripMargin,
    '\n'
  )

  def testTypingAfterIndentBeforeIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  $CARET
       |  expr
       |""".stripMargin,
    s"""
       |def test = {
       |  e$CARET
       |  expr
       |}
       |""".stripMargin,
    s"""
       |def test =
       |  e$CARET
       |  expr
       |""".stripMargin,
    'e'
  )

  def testTypingAfterDoubleIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   $CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |   e$CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |   e$CARET
       |""".stripMargin,
    'e'
  )

  def testTypingAfterSecondIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   .prod
       |  $CARET
       |""".stripMargin,
    s"""
       |def test = {
       |  expr
       |   .prod
       |  e$CARET
       |}
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |   .prod
       |  e$CARET
       |""".stripMargin,
    'e'
  )

  def testEnterAfterSecondIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   + expr
       |  $CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |   + expr
       |$indent
       |$CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |   + expr
       |$indent
       |$CARET
       |""".stripMargin,
    '\n'
  )

  def testTypingInUnindented(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |$CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |e$CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |e$CARET
       |""".stripMargin,
    'e'
  )

  def testTypingBetweenCommentAndIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  // test
       |  $CARET
       |  expr
       |""".stripMargin,
    s"""
       |def test = {
       |  // test
       |  e$CARET
       |  expr
       |}
       |""".stripMargin,
    s"""
       |def test =
       |  // test
       |  e$CARET
       |  expr
       |
       |""".stripMargin,
    'e'
  )


  def testEnterInsideOfIndentedCall(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  call($CARET)
       |""".stripMargin,
    s"""
       |def test =
       |  call(
       |    $CARET
       |  )
       |""".stripMargin,
    s"""
       |def test =
       |  call(
       |    $CARET
       |  )
       |""".stripMargin,
    '\n'
  )


  def testTypingInsideOfIndentedCall(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  call(
       |  $CARET
       |  )
       |""".stripMargin,
    s"""
       |def test =
       |  call(
       |  e$CARET
       |  )
       |""".stripMargin,
    s"""
       |def test =
       |  call(
       |  e$CARET
       |  )
       |""".stripMargin,
    'e'
  )

  def testDeletingLastExprBefore(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  e$CARET
       |  expr
       |}
       |""".stripMargin,
    s"""
       |def test =
       |  $CARET
       |  expr
       |""".stripMargin,
    s"""
       |def test = {
       |  $CARET
       |  expr
       |}
       |""".stripMargin,
  )

  def testDeletingLastExprAfter(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  expr
       |  e$CARET
       |}
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin,
    s"""
       |def test = {
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
  )

  def testDeletingLastExprWithComment(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  // comment
       |  expr
       |  e$CARET
       |}
       |""".stripMargin,
    s"""
       |def test = {
       |  // comment
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
    s"""
       |def test = {
       |  // comment
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
  )

  def testDeletingLastExprWithStatement(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  val x = expr
       |  x$CARET
       |}
       |""".stripMargin,
    s"""
       |def test = {
       |  val x = expr
       |  $CARET
       |}
       |""".stripMargin,
    s"""
       |def test = {
       |  val x = expr
       |  $CARET
       |}
       |""".stripMargin,
  )

  /**************************************** Test in multiple contexts *************************************************/
  val contexts = Seq(
    """
      |def test =$BODY$
      |""".stripMargin,
    """
      |val test =$BODY$
      |""".stripMargin,
    """
      |if (cond)$BODY$
      |""".stripMargin,
    """
      |if (cond) thenBranch
      |else$BODY$
      |""".stripMargin,
    """
      |for (x <- xs)$BODY$
      |""".stripMargin,
    """
      |for (x <- xs) yield$BODY$
      |""".stripMargin,
    """
      |while (cond)$BODY$
      |""".stripMargin,
    """
      |try something
      |finally$BODY$
      |""".stripMargin,
  )

  def checkBackspaceInAllContexts(bodyBefore: String, bodyAfter: String, bodyAfterWithSettingsTurnedOff: String): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff)(checkGeneratedTextAfterBackspace)

  def checkTypingInAllContexts(bodyBefore: String, bodyAfter: String, bodyAfterWithSettingsTurnedOff: String, typedChar: Char): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff)(checkGeneratedTextAfterTyping(_, _, typedChar))

  def checkInAllContexts(bodyBefore: String, bodyAfter: String, bodyAfterWithSettingsTurnedOff: String)(check: (String, String) => Unit): Unit = {
    def transform(body: String): String =
      body.trim.replace("def test =", "")

    val settings = ScalaApplicationSettings.getInstance()

    for (context <- contexts) {
      def buildBody(body: String): String =
        context.replace("$BODY$", transform(body))

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
