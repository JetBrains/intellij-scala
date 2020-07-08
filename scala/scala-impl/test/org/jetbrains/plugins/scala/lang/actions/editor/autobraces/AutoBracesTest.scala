package org.jetbrains.plugins.scala
package lang
package actions
package editor
package autobraces

class AutoBracesTest extends AutoBraceTestBase {
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
}