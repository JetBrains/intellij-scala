package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

class AutoBraceBackspaceTest extends AutoBraceTestBase {

  def checkBackspaceInAllContexts(bodyBefore: String,
                                  bodyAfter: String,
                                  bodyAfterWithSettingsTurnedOff: String): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, allContexts, checkGeneratedTextAfterBackspace)

  def checkBackspaceInUncontinuedContexts(bodyBefore: String,
                                          bodyAfter: String,
                                          bodyAfterWithSettingsTurnedOff: String): Unit =
    checkInAllContexts(bodyBefore, bodyAfter, bodyAfterWithSettingsTurnedOff, uncontinuedContexts, checkGeneratedTextAfterBackspace)

  def testDeletingLastExprBefore(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  e$CARET
       |  expr
       |}
       |""".stripMargin,
    s"""
       |  $CARET
       |  expr
       |""".stripMargin,
    s""" {
       |  $CARET
       |  expr
       |}
       |""".stripMargin,
  )

  def testDeletingLastExprAfter(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  expr
       |  e$CARET
       |}
       |""".stripMargin,
    s"""
       |  expr
       |  $CARET
       |""".stripMargin,
    s""" {
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
  )

  def testDeletingSecondToLastExprWithComment(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  // comment
       |  expr
       |  e$CARET
       |}
       |""".stripMargin,
    s""" {
       |  // comment
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
    s""" {
       |  // comment
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
  )

  def testDeletingLastExprWithComment(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  // blub
       |  x$CARET
       |}
       |""".stripMargin,
    s""" {
       |  // blub
       |  $CARET
       |}
       |""".stripMargin,
    s""" {
       |  // blub
       |  $CARET
       |}
       |""".stripMargin,
  )

  def testDeletingLastExprWithStatement(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  val x = expr
       |  x$CARET
       |}
       |""".stripMargin,
    s""" {
       |  val x = expr
       |  $CARET
       |}
       |""".stripMargin,
    s""" {
       |  val x = expr
       |  $CARET
       |}
       |""".stripMargin,
  )

  /*def testDeletingQuotes(): Unit = checkBackspaceInAllContexts(
    s"""{
       |  expr
       |  "$CARET"
       |}
       |""".stripMargin,
    s"""
       |  expr
       |  $CARET
       |""".stripMargin,
    s"""{
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
  )*/

  def testDeletingDoubleParenthesis(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  expr
       |  ($CARET)
       |}
       |""".stripMargin,
    s"""
       |  expr
       |  $CARET
       |""".stripMargin,
    s""" {
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
  )

  // SCL-17867
  def testDeletingSingleParenthesis(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  expr
       |  ($CARET
       |}
       |""".stripMargin,
    s"""
       |  expr
       |  $CARET
       |""".stripMargin,
    s""" {
       |  expr
       |  $CARET
       |}
       |""".stripMargin,
  )

  def testDeletingOnlyPartOfSecondToLastExpr(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  expr
       |  call(
       |
       |  )$CARET
       |}
       |""".stripMargin,
    s""" {
       |  expr
       |  call(
       |
       |  $CARET
       |}
       |""".stripMargin,
    s""" {
       |  expr
       |  call(
       |
       |  $CARET
       |}
       |""".stripMargin,
  )

  // SCL-18041
  def testDeletingInner(): Unit = checkBackspaceInAllContexts(
    s""" {
       |  if (true)
       |    a$CARET
       |}
       |""".stripMargin,
    s""" {
       |  if (true)
       |    $CARET
       |}
       |""".stripMargin,
    s""" {
       |  if (true)
       |    $CARET
       |}
       |""".stripMargin,
  )
}
