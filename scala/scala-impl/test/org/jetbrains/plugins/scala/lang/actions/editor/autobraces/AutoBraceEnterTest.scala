package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

class AutoBraceEnterTest extends AutoBraceTestBase {

  def checkEnterInAllContexts(bodyBefore: String,
                              bodyAfter: String,
                              bodyAfterWithSettingsTurnedOff: String): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, allContexts, checkGeneratedTextAfterEnter)

  def checkEnterInUncontinuedContexts(bodyBefore: String,
                                      bodyAfter: String,
                                      bodyAfterWithSettingsTurnedOff: String): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, uncontinuedContexts, checkGeneratedTextAfterEnter)

  def testEnterAfterExpr(): Unit = checkEnterInUncontinuedContexts(
    s"""
       |  expr$CARET
       |""".stripMargin,
    s"""
       |  expr
       |  $CARET
       |""".stripMargin,
    s"""
       |  expr
       |$CARET
       |""".stripMargin
  )

  def testEnterAfterExpr_AfterComment(): Unit = checkEnterInUncontinuedContexts(
    s"""
       |  expr //comment$CARET
       |""".stripMargin,
    s"""
       |  expr //comment
       |  $CARET
       |""".stripMargin,
    s"""
       |  expr //comment
       |$CARET
       |""".stripMargin
  )

  def testEnterAfterExpr_AfterComment_OnPrevLine(): Unit = checkEnterInUncontinuedContexts(
    s"""
       |  expr //comment$CARET
       |""".stripMargin,
    s"""
       |  expr //comment
       |  $CARET
       |""".stripMargin,
    s"""
       |  expr //comment
       |$CARET
       |""".stripMargin
  )

  def testEnterAfterExprAndIndentation(): Unit = checkEnterInUncontinuedContexts(
    s"""
       |  expr
       |  $CARET
       |""".stripMargin,
    s"""
       |  expr
       |$indent
       |$CARET
       |""".stripMargin,
    s"""
       |  expr
       |$indent
       |$CARET
       |""".stripMargin
  )

  def testEnterBeforeIndentedExpr(): Unit = checkEnterInAllContexts(
    s""" $CARET
       |  expr
       |""".stripMargin,
    s"""$space
       |  $CARET
       |  expr
       |""".stripMargin,
    s"""$space
       |  $CARET
       |  expr
       |""".stripMargin
  )

  def testEnterAfterSecondIndentation(): Unit = checkEnterInAllContexts(
    s"""  expr
       |   + expr
       |  $CARET
       |""".stripMargin,
    s"""  expr
       |   + expr
       |$indent
       |$CARET
       |""".stripMargin,
    s"""  expr
       |   + expr
       |$indent
       |$CARET
       |""".stripMargin
  )


  def testEnterInsideOfIndentedCall(): Unit = checkEnterInAllContexts(
    s"""
       |  call($CARET)
       |""".stripMargin,
    s"""
       |  call(
       |    $CARET
       |  )
       |""".stripMargin,
    s"""
       |  call(
       |    $CARET
       |  )
       |""".stripMargin
  )

  def testEnterInDoubleIndention(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |  if (true)$CARET
       |""".stripMargin,
    s"""
       |  if (true)
       |    $CARET
       |""".stripMargin
  )

  def testEnterAfterBlock(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |  if (true) {
       |    expr
       |  }$CARET
       |""".stripMargin,
    s"""
       |  if (true) {
       |    expr
       |  }
       |  $CARET
       |""".stripMargin
  )

  def testUnIndentElseAfterEnter(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |if (true)
       |  expr
       |  else$CARET
       |""".stripMargin,
    s"""
       |if (true)
       |  expr
       |else
       |  $CARET
       |""".stripMargin
  )

  def testUnIndentCatchAfterEnter(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |try
       |  expr
       |  catch$CARET
       |""".stripMargin,
    s"""
       |try
       |  expr
       |catch
       |  $CARET
       |""".stripMargin
  )

  def testUnIndentFinallyAfterEnter(): Unit = checkGeneratedTextAfterEnter(
    s"""
       |try
       |  expr
       |  finally$CARET
       |""".stripMargin,
    s"""
       |try
       |  expr
       |finally
       |  $CARET
       |""".stripMargin
  )
}
