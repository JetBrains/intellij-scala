package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

class AutoBraceEnterTest extends AutoBraceTestBase {

  def testEnterAfterExpr(): Unit = checkTypingInUncontinuedContexts(
    s"""
       |def test =
       |  expr$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |$CARET
       |""".stripMargin -> ContinuationOnNewline,
    '\n'
  )

  def testEnterAfterExprAndIndentation(): Unit = checkTypingInUncontinuedContexts(
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |$indent
       |$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |$indent
       |$CARET
       |""".stripMargin -> ContinuationOnNewline,
    '\n'
  )

  def testEnterBeforeIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test = $CARET
       |  expr
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =$space
       |  $CARET
       |  expr
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =$space
       |  $CARET
       |  expr
       |""".stripMargin -> ContinuationOnNewline,
    '\n'
  )

  def testEnterAfterSecondIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   + expr
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |   + expr
       |$indent
       |$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |   + expr
       |$indent
       |$CARET
       |""".stripMargin -> ContinuationOnNewline,
    '\n'
  )


  def testEnterInsideOfIndentedCall(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  call($CARET)
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  call(
       |    $CARET
       |  )
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  call(
       |    $CARET
       |  )
       |""".stripMargin -> ContinuationOnNewline,
    '\n'
  )

}
