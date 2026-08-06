package org.jetbrains.plugins.scala.lang.actions.editor

/** TODO: unify with [[org.jetbrains.plugins.scala.codeInsight.editorActions.ScalaQuoteHandlerTest]] */
class StringTypingTest extends EditorTypeActionTestBase {

  override protected def typedChar: Char = '"'

  def testInsertQuoteWhenTypingInsideMultilineString(): Unit = doRepetitiveTypingTest(
    s"""$qqq some content $CARET $qqq""",
    s"""$qqq some content $q$CARET $qqq""",
    s"""$qqq some content $qq$CARET $qqq"""
  )

  def testInsertQuoteWhenTypingInsideMultilineString_Interpolated(): Unit = doRepetitiveTypingTest(
    s"""s$qqq some content $CARET $qqq""",
    s"""s$qqq some content $q$CARET $qqq""",
    s"""s$qqq some content $qq$CARET $qqq"""
  )

  // Just moving caret

  def testMoveCaretWhenTypingInTheEndOfString(): Unit = doRepetitiveTypingTest(
    s"""$CARET""",
    s"""$q$CARET$q""",
    s"""$qq$CARET""",
    s"""$qqq$CARET$qqq""",
    s"""$qqq$q$CARET$qq""",
    s"""$qqq$qq$CARET$q""",
    s"""$qqq$qqq$CARET"""
  )

  def testMoveCaretWhenTypingInTheEndOfString_Interpolated(): Unit = doRepetitiveTypingTest(
    s"""s$CARET""",
    s"""s$q$CARET$q""",
    s"""s$qq$CARET""",
    s"""s$qqq$CARET$qqq""",
    s"""s$qqq$q$CARET$qq""",
    s"""s$qqq$qq$CARET$q""",
    s"""s$qqq$qqq$CARET"""
  )

  def testMoveCaretWhenTypingInTheEndOfSimpleString_WithSomeContent(): Unit = doRepetitiveTypingTest(
    s"""$q some content $CARET$q""",
    s"""$q some content $q$CARET"""
  )

  def testMoveCaretWhenTypingInTheEndOfSimpleString_Interpolated_WithSomeContent(): Unit = doRepetitiveTypingTest(
    s"""s$q some content $CARET$q""",
    s"""s$q some content $q$CARET"""
  )

  def testMoveCaretWhenTypingInTheEndOfMultilineString_WithSomeContent(): Unit = doRepetitiveTypingTest(
    s"""$qqq some content $CARET$qqq""",
    s"""$qqq some content $q$CARET$qq""",
    s"""$qqq some content $qq$CARET$q""",
    s"""$qqq some content $qqq$CARET"""
  )

  def testMoveCaretWhenTypingInTheEndOfMultilineString_Interpolated_WithSomeContent(): Unit = doRepetitiveTypingTest(
    s"""s$qqq some content $CARET$qqq""",
    s"""s$qqq some content $q$CARET$qq""",
    s"""s$qqq some content $qq$CARET$q""",
    s"""s$qqq some content $qqq$CARET"""
  )

  def testTypeQuiteInComment(): Unit = {
    // in the beginning of the file
    doRepetitiveTypingTest(
      s"""//$CARET""",
      s"""//$q$CARET""",
      s"""//$qq$CARET""",
      s"""//$qqq$CARET""",
    )

    // with spaces before comment
    doRepetitiveTypingTest(
      s"""   //$CARET""",
      s"""   //$q$CARET""",
      s"""   //$qq$CARET""",
      s"""   //$qqq$CARET""",
    )

    doRepetitiveTypingTest(
      s"""class A {
         |  //$CARET
         |}""".stripMargin,
    s"""class A {
         |  //$q$CARET
         |}""".stripMargin
    )
  }
}
