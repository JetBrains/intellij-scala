package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

class AutoBraceBackspaceTest extends AutoBraceTestBase {
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
}
