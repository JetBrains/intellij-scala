package org.jetbrains.plugins.scala
package lang
package actions
package editor

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class AutoBracesTest extends EditorActionTestBase {
  val space = " "
  val indent = "  "


  def testBlubBlub(): Unit = checkGeneratedTextAfterTyping(
    s"""
       |for (e <- expr)
       |  $CARET
       |  expr
       |""".stripMargin,
    s"""
       |for (e <- expr) {
       |  e$CARET
       |  expr
       |}
       |""".stripMargin,
    'e'
  )


  def testEnterAfterExpr(): Unit = checkInAllContexts(
    s"""
       |def test =
       |  expr$CARET
       |""".stripMargin,
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin,
    '\n'
  )

  def testEnterAfterExprAndIndentation(): Unit = checkInAllContexts(
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
    '\n'
  )

  def testTypingAfterIndentation(): Unit = checkInAllContexts(
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
    'e'
  )

  def testEnterBeforeIndentedExpr(): Unit = checkInAllContexts(
    s"""
       |def test = $CARET
       |  expr
       |""".stripMargin,
    s"""
       |def test =$space
       |  $CARET
       |  expr
       |""".stripMargin,
    '\n'
  )

  def testTypingAfterIndentBeforeIndentedExpr(): Unit = checkInAllContexts(
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
    'e'
  )

  def testTypingAfterDoubleIndentation(): Unit = checkInAllContexts(
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
    'e'
  )

  def testTypingAfterSecondIndentation(): Unit = checkInAllContexts(
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
    'e'
  )

  def testEnterAfterSecondIndentation(): Unit = checkInAllContexts(
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
    '\n'
  )

  def testTypingInUnindented(): Unit = checkInAllContexts(
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
    'e'
  )

  def testTypingBetweenCommentAndIndentedExpr(): Unit = checkInAllContexts(
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
    'e'
  )


  def testEnterInsideOfIndentedCall(): Unit = checkInAllContexts(
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
    '\n'
  )


  def testTypingInsideOfIndentedCall(): Unit = checkInAllContexts(
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
    'e'
  )


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
      |try somthing
      |finally$BODY$
      |""".stripMargin,
  )

  def checkInAllContexts(bodyBefore: String, bodyAfter: String, typedChar: Char): Unit = {
    for (context <- contexts) {
      checkGeneratedTextAfterTyping(
        context.replace("$BODY$", bodyBefore.trim.replace("def test =", "")),
        context.replace("$BODY$", bodyAfter.trim.replace("def test =", "")),
        typedChar
      )
    }
  }
}
