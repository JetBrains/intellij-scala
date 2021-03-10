package org.jetbrains.plugins.scala
package lang
package actions
package editor
package autobraces

class AutoBracesTypingTest extends AutoBraceTestBase {
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
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  expr
       |  x$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  expr
       |  x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testTypingAfterIndentBeforeIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  $CARET
       |  expr
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  x$CARET
       |  expr
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  x$CARET
       |  expr
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testTypingAfterDoubleIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |   x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |   x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testTypingAfterSecondIndentation(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |   .prod
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  expr
       |   .prod
       |  x$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  expr
       |   .prod
       |  x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testTypingInUnindented(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testTypingAfterEmptyLinesAndIndent(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  expr
       |
       |  x$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  expr
       |
       |  x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  // SCL-17866
  def testTypingAfterEmptyLinesWithOnlySpacesAndIndent(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr
       |$indent$indent
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  expr
       |$indent$indent
       |  x$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  expr
       |$indent$indent
       |  x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testTypingBetweenCommentAndIndentedExpr(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  // test
       |  $CARET
       |  expr
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  // test
       |  x$CARET
       |  expr
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  // test
       |  x$CARET
       |  expr
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testTypingInsideOfIndentedCall(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  call(
       |  $CARET
       |  )
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  call(
       |  x$CARET
       |  )
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  call(
       |  x$CARET
       |  )
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  // SCL-17794
  def testClosingStringQuote(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  "test"
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  "test"
       |  "$CARET"
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  "test"
       |  "$CARET"
       |""".stripMargin -> ContinuationOnNewline,
    '\"'
  )

  // SCL-17793
  def testNoAutoBraceOnDot(): Unit = checkTypingInUncontinuedContexts(
    s"""
       |def test =
       |  expr
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |    .$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr
       |    .$CARET
       |""".stripMargin -> ContinuationOnNewline,
    '.'
  )

  def testTypingBeforeContinuation(): Unit = checkTypingInContinuedContexts(
    s"""
       |try
       |  expr
       |  $CARET
       |finally ()
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |try {
       |  expr
       |  a$CARET
       |} finally ()
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |try
       |  expr
       |  a$CARET
       |finally ()
       |""".stripMargin -> ContinuationOnSameLine,
    'a'
  )

  // todo: fix SCL-17843
  /*def testMultilineBeforeContinuation(): Unit = checkTypingInContinuedContexts(
    s"""
       |try
       |  expr
       |  $CARET
       |
       |
       |
       |
       |finally ()
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |try {
       |  expr
       |  a$CARET
       |
       |
       |
       |
       |} finally ()
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |try
       |  expr
       |  a$CARET
       |
       |
       |
       |
       |finally ()
       |""".stripMargin -> ContinuationOnSameLine,
    'a'
  )*/

  def testTypingAContinuationOfEquallyIndented(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  if (true) 3
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  if (true) 3
       |  e$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  if (true) 3
       |  e$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'e'
  )

  def testAbandoningAContinuationOfEquallyIndented(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  if (true) 3
       |  e$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  if (true) 3
       |  ex$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  if (true) 3
       |  ex$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testTypingANonContinuationOfEquallyIndented(): Unit = checkTypingInUncontinuedContexts(
    s"""
       |def test =
       |  if (true) 3
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  if (true) 3
       |  t$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  if (true) 3
       |  t$CARET
       |""".stripMargin -> ContinuationOnNewline,
    't'
  )

  def testContinuingPostfix(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr +
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr +
       |    x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |  expr +
       |    x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testNotContinuingPostfix(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  expr +
       |
       |  $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  expr +
       |
       |  x$CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  expr +
       |
       |  x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )

  def testFinishingVal(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  val$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  val $CARET
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  val $CARET
       |""".stripMargin -> ContinuationOnNewline,
    ' '
  )

  def testFinishingValOnSameLine(): Unit = checkTypingInAllContexts(
    s"""
       |def test = val$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = val $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = val $CARET
       |""".stripMargin -> ContinuationOnNewline,
    ' '
  )

  def testFinishingValOnSameIndent(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |val$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |val $CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |val $CARET
       |""".stripMargin -> ContinuationOnNewline,
    ' '
  )

  def testFinishingValWithSomethingBehind(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  val${CARET}expr
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  val ${CARET}expr
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  val ${CARET}expr
       |""".stripMargin -> ContinuationOnNewline,
    ' '
  )

  def testFinishingValWithDirectMultilineInfixBehind(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  val${CARET}expr +
       |    otherExpr
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  val ${CARET}expr +
       |    otherExpr
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  val ${CARET}expr +
       |    otherExpr
       |""".stripMargin -> ContinuationOnNewline,
    ' '
  )

  def testFinishingValWithMultilineInfixBehind(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  val${CARET}-expr +
       |    otherExpr
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  val ${CARET}-expr +
       |    otherExpr
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  val ${CARET}-expr +
       |    otherExpr
       |""".stripMargin -> ContinuationOnNewline,
    ' '
  )


  def testFinishingValWithPrecedingComment(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  // test
       |  val${CARET}
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  // test
       |  val ${CARET}
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  // test
       |  val ${CARET}
       |""".stripMargin -> ContinuationOnNewline,
    ' '
  )


  def testFinishingValAfterDirectWithPrecedingComment(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |  // test
       |  val${CARET}x
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test = {
       |  // test
       |  val ${CARET}x
       |}
       |""".stripMargin -> ContinuationOnSameLine,
    s"""
       |def test =
       |  // test
       |  val ${CARET}x
       |""".stripMargin -> ContinuationOnNewline,
    ' '
  )

  def testFinishingUnindentedVal(): Unit = checkGeneratedTextAfterTyping(
    s"""
       |def test = {
       |  if (true) return
       |  val${CARET}
       |}
       |""".stripMargin,
    s"""
       |def test = {
       |  if (true) return
       |  val ${CARET}
       |}
       |""".stripMargin,
    ' '
  )

  def testFinishingIndentedVal(): Unit = checkGeneratedTextAfterTyping(
    s"""
       |def test = {
       |  if (true) return
       |    val${CARET}
       |}
       |""".stripMargin,
    s"""
       |def test = {
       |  if (true) return
       |    val ${CARET}
       |}
       |""".stripMargin,
    ' '
  )

  //
  def testBracesOnNextLineStyle(): Unit = checkTypingInAllContexts(
    s"""
       |def test =
       |{
       |  expr
       |}
       |
       |$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |{
       |  expr
       |}
       |
       |x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    s"""
       |def test =
       |{
       |  expr
       |}
       |
       |x$CARET
       |""".stripMargin -> ContinuationOnNewline,
    'x'
  )
}