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

  def checkTypingInAllContexts_DoesNotWrapBodyWithBraces(bodyBefore: String, bodyAfter: String, typedChar: Char): Unit = {
    allContexts.foreach(checkInContext(bodyBefore, bodyAfter, bodyAfter, _, checkGeneratedTextAfterTyping(_, _, typedChar)))
  }

  def checkTypingInAllContexts_WrapsBodyWithBraces(bodyBefore: String, typedText: String): Unit = {
    allContexts.foreach(checkTyping_WrapsBodyWithBraces(bodyBefore, _, typedText))
  }

  def checkTypingInUncontinuedContexts_WrapsBodyWithBraces(bodyBefore: String, typedChar: Char): Unit = {
    uncontinuedContexts.foreach(checkTyping_WrapsBodyWithBraces(bodyBefore, _, typedChar.toString))
  }

  def checkTypingInAllContexts_WrapsBodyWithBraces(bodyBefore: String, typedChar: Char): Unit = {
    checkTypingInAllContexts_WrapsBodyWithBraces(bodyBefore, typedChar.toString)
  }

  def checkTyping_WrapsBodyWithBraces(
    bodyBefore: String,
    context: BodyContext,
    typedText: String,
  ): Unit = {
    val before = injectCodeWithIndentAdjust(bodyBefore, context.textWithoutContinuationPlaceholder)

    val bodyWithTypedText = s"""${bodyBefore.replace(CARET, typedText + CARET)}"""

    val afterWithSettingsTurnedOff = injectCodeWithIndentAdjust(bodyWithTypedText, context.textWithoutContinuationPlaceholder)

    // for code with continuation, insert closing brace just before the continuation (with extra space)
    // for code without continuation insert closing brace after the body actual (assuming body ends with single new line)
    val after =
      if (context.withContinuation)
        injectCodeWithIndentAdjust(s" {$bodyWithTypedText", context.text).replace(ContinuationPlaceholder, "} ")
      else
        injectCodeWithIndentAdjust(s" {$bodyWithTypedText}\n", context.text)

    checkWithSettingsOnAndOf(before, after, afterWithSettingsTurnedOff, checkGeneratedTextAfterTypingText(_, _, typedText))
  }

  def testTypingAfterIndentation(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  expr
       |  $CARET
       |""".stripMargin,
    'x'
  )

  def testTypingAfterIndentation_AfterComment(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  expr // comment
       |  $CARET
       |""".stripMargin,
    'x'
  )

  def testTypingAfterIndentBeforeIndentedExpr(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  $CARET
       |  expr
       |""".stripMargin,
    'x'
  )

  def testTypingAfterDoubleIndentation(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s"""
       |  expr
       |   $CARET
       |""".stripMargin,
    s"""
       |  expr
       |   x$CARET
       |""".stripMargin,
    'x'
  )

  def testTypingAfterSecondIndentation(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  expr
       |   .prod
       |  $CARET
       |""".stripMargin,
    'x'
  )

  def testTypingInUnindented(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s"""
       |  expr
       |$CARET
       |""".stripMargin,
    s"""
       |  expr
       |x$CARET
       |""".stripMargin,
    'x'
  )

  def testTypingAfterEmptyLinesAndIndent(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  expr
       |
       |  $CARET
       |""".stripMargin,
    'x'
  )

  // SCL-17866
  def testTypingAfterEmptyLinesWithOnlySpacesAndIndent(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  expr
       |$indent$indent
       |  $CARET
       |""".stripMargin,
    'x'
  )

  def testTypingBetweenCommentAndIndentedExpr(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  // test
       |  $CARET
       |  expr
       |""".stripMargin,
    'x'
  )

  def testTypingInsideOfIndentedCall(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s"""
       |  call(
       |  $CARET
       |  )
       |""".stripMargin,
    s"""
       |  call(
       |  x$CARET
       |  )
       |""".stripMargin,
    'x'
  )

  // SCL-17794
  def testClosingStringQuote(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  "test"
       |  $CARET
       |""".stripMargin,
    "\"\""
  )

  // SCL-17793
  def testNoAutoBraceOnDot(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s"""
       |  expr
       |  $CARET
       |""".stripMargin,
    s"""
       |  expr
       |    .$CARET
       |""".stripMargin,
    '.'
  )

  // todo: fix SCL-17843
  /*def testMultilineBeforeContinuation(): Unit = checkTypingInContinuedContexts(
    s"""
       |  expr
       |  $CARET
       |
       |
       |
       |
       |""".stripMargin,
    s""" {
       |  expr
       |  a$CARET
       |
       |
       |
       |
       |}
       |""".stripMargin,
    s"""
       |  expr
       |  a$CARET
       |
       |
       |
       |
       |""".stripMargin,
    'a'
  )*/

  def testTypingAContinuationOfEquallyIndented(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s"""
       |  if (true) 3
       |  $CARET
       |""".stripMargin,
    s"""
       |  if (true) 3
       |  e$CARET
       |""".stripMargin,
    'e'
  )

  def testAbandoningAContinuationOfEquallyIndented(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  if (true) 3
       |  e$CARET
       |""".stripMargin,
    'x'
  )

  def testTypingANonContinuationOfEquallyIndented(): Unit = checkTypingInUncontinuedContexts_WrapsBodyWithBraces(
    s"""
       |  if (true) 3
       |  $CARET
       |""".stripMargin,
    't'
  )

  def testContinuingPostfix(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s"""
       |  expr +
       |  $CARET
       |""".stripMargin,
    s"""
       |  expr +
       |    x$CARET
       |""".stripMargin,
    'x'
  )

  def testNotContinuingPostfix(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  expr +
       |
       |  $CARET
       |""".stripMargin,
    'x'
  )

  def testFinishingVal(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  val$CARET
       |""".stripMargin,
    ' '
  )

  def testFinishingValOnSameLine(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s""" val$CARET
       |""".stripMargin,
    s""" val $CARET
       |""".stripMargin,
    ' '
  )

  def testFinishingValOnSameIndent(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s"""
       |val$CARET
       |""".stripMargin,
    s"""
       |val $CARET
       |""".stripMargin,
    ' '
  )

  def testFinishingValWithSomethingBehind(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  val${CARET}expr
       |""".stripMargin,
    ' '
  )

  def testFinishingValWithDirectMultilineInfixBehind(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  val${CARET}expr +
       |    otherExpr
       |""".stripMargin,
    ' '
  )

  def testFinishingValWithMultilineInfixBehind(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  val$CARET-expr +
       |    otherExpr
       |""".stripMargin,
    ' '
  )

  def testFinishingValWithPrecedingComment(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  // test
       |  val$CARET
       |""".stripMargin,
    ' '
  )


  def testFinishingValAfterDirectWithPrecedingComment(): Unit = checkTypingInAllContexts_WrapsBodyWithBraces(
    s"""
       |  // test
       |  val${CARET}x
       |""".stripMargin,
    ' '
  )

  def testFinishingUnindentedVal(): Unit = checkGeneratedTextAfterTyping(
    s""" {
       |  if (true) return
       |  val$CARET
       |}
       |""".stripMargin,
    s""" {
       |  if (true) return
       |  val $CARET
       |}
       |""".stripMargin,
    ' '
  )

  def testFinishingIndentedVal(): Unit = checkGeneratedTextAfterTyping(
    s""" {
       |  if (true) return
       |    val$CARET
       |}
       |""".stripMargin,
    s""" {
       |  if (true) return
       |    val $CARET
       |}
       |""".stripMargin,
    ' '
  )

  //
  def testBracesOnNextLineStyle(): Unit = checkTypingInAllContexts_DoesNotWrapBodyWithBraces(
    s"""
       |{
       |  expr
       |}
       |
       |$CARET
       |""".stripMargin,
    s"""
       |{
       |  expr
       |}
       |
       |x$CARET
       |""".stripMargin,
    'x'
  )

  def test_before_brace(): Unit = checkGeneratedTextAfterTyping(
    s"""
       |val x =
       |  object$CARET{}
       |""".stripMargin,
    s"""
       |val x = {
       |  object $CARET{}
       |}
       |""".stripMargin,
    ' '
  )
}