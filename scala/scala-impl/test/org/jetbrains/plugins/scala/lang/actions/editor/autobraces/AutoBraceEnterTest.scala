package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

class AutoBraceEnterTest extends AutoBraceTestBase {

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

}
