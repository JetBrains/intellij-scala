package org.jetbrains.plugins.scala.lang.actions.editor.autobraces

class AutoBraceBackspaceTest extends AutoBraceTestBase {
  def testDeletingLastExprBefore(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  e$CARET
       |  expr
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  $CARET
       |  expr
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  $CARET
       |  expr
       |}
       |""".stripMargin -> ContinuationOnSameLine,
  )

  def testDeletingLastExprAfter(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  expr
       |  e$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  expr
       |  $CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
  )

  def testDeletingSecondToLastExprWithComment(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  // comment
       |  expr
       |  e$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test = {
       |  // comment
       |  expr
       |  $CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test = {
       |  // comment
       |  expr
       |  $CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
  )

  def testDeletingLastExprWithComment(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  // blub
       |  x$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test = {
       |  // blub
       |  $CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test = {
       |  // blub
       |  $CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
  )

  def testDeletingLastExprWithStatement(): Unit = checkBackspaceInAllContexts(
    s"""
       |def test = {
       |  val x = expr
       |  x$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test = {
       |  val x = expr
       |  $CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test = {
       |  val x = expr
       |  $CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
  )
}
