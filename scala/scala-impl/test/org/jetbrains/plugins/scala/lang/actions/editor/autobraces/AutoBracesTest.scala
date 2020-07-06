package org.jetbrains.plugins.scala
package lang
package actions
package editor
package autobraces

class AutoBracesTest extends AutoBraceTestBase {
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
       |  x$CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  expr
       |  x$CARET
       |""".stripMargin -> NextConstructOnNewline,
    'x'
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
       |  x$CARET
       |  expr
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  x$CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    'x'
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
       |   x$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |   x$CARET
       |""".stripMargin -> NextConstructOnNewline,
    'x'
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
       |  x$CARET
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  expr
       |   .prod
       |  x$CARET
       |""".stripMargin -> NextConstructOnNewline,
    'x'
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
       |x$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |x$CARET
       |""".stripMargin -> NextConstructOnNewline,
    'x'
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
       |  x$CARET
       |  expr
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  // test
       |  x$CARET
       |  expr
       |""".stripMargin -> NextConstructOnNewline,
    'x'
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
       |  x$CARET
       |  )
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  call(
       |  x$CARET
       |  )
       |""".stripMargin -> NextConstructOnNewline,
    'x'
  )

  // SCL-17794
  def testClosingStringQuote(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  "test"
       |  $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test = {
       |  "test"
       |  "$CARET"
       |}
       |""".stripMargin -> NextConstructOnSameLine,
    s"""
       |def test =
       |  "test"
       |  "$CARET"
       |""".stripMargin -> NextConstructOnNewline,
    '\"'
  )

  // SCL-17793
  def testNoAutoBraceOnDot(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |  .$CARET
       |""".stripMargin -> NextConstructOnNewline,
    s"""
       |def test =
       |  expr
       |  .$CARET
       |""".stripMargin -> NextConstructOnNewline,
    '.',
    checkContextsWithPostfix = false
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

}