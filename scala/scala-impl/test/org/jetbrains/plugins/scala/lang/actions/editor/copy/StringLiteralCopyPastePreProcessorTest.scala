package org.jetbrains.plugins.scala.lang.actions.editor.copy

/**
 * @see [[MultiLineStringCopyPastePreProcessorTest]]
 */
class StringLiteralCopyPastePreProcessorTest extends CopyPasteTestBase {

  def testEscapeTextWhenPastingToString_CaretInTheBeginning(): Unit = doPasteTest(
    s"""text with quotes (")""",
    s"""val v = "$CARET aaa bbb"""",
    s"""val v = "text with quotes (\\")$CARET aaa bbb"""",
  )

  def testEscapeTextWhenPastingToString_CaretInTheMiddle(): Unit = doPasteTest(
    s"""text with quotes (")""",
    s"""val v = "aaa $CARET bbb"""",
    s"""val v = "aaa text with quotes (\\")$CARET bbb"""",
  )

  def testEscapeTextWhenPastingToString_CaretInTheEnd(): Unit = doPasteTest(
    s"""text with quotes (")""",
    s"""val v = "aaa bbb $CARET"""",
    s"""val v = "aaa bbb text with quotes (\\")$CARET"""",
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_CaretInTheBeginning(): Unit = doPasteTest(
    s"""text with quotes (")""",
    s"""val v = "$START$CARET aaa$END bbb"""",
    s"""val v = "text with quotes (\\")$CARET bbb"""",
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_CaretInTheMiddle(): Unit = doPasteTest(
    s"""text with quotes (")""",
    s"""val v = "aaa $START$CARET bbb$END"""",
    s"""val v = "aaa text with quotes (\\")$CARET"""",
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_CaretInTheEnd(): Unit = doPasteTest(
    s"""text with quotes (")""",
    s"""val v = "aaa ${START}bbb $CARET$END"""",
    s"""val v = "aaa text with quotes (\\")$CARET"""",
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_SelectionEndOutsideLiteral_CaretOutsideLiteral(): Unit = doPasteTest(
    s"""text with quote in the end"""",
    s"""val v = "aaa ${START}bbb"$CARET$END + "ccc"""",
    s"""val v = "aaa text with quote in the end"$CARET + "ccc"""",
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_SelectionEndOutsideLiteral_CaretOutsideLiteral_EOF(): Unit = doPasteTest(
    s"""text with quote in the end"""",
    s"""val v = "aaa ${START}bbb"$CARET$END""",
    s"""val v = "aaa text with quote in the end"$CARET""",
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_SelectionEndOutsideLiteral_CaretInsideLiteral(): Unit = doPasteTest(
    s"""text with quote in the end"""",
    s"""val v = "aaa $START${CARET}bbb"$END + "ccc"""",
    s"""val v = "aaa text with quote in the end"$CARET + "ccc"""",
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_SelectionOutsideLiteral_CaretInsideLiteral(): Unit = doPasteTest(
    s""""text with quotes at both sides"""",
    s"""val v = $START "aaa ${CARET}bbb" $END + "ccc"""",
    s"""val v = "text with quotes at both sides"$CARET + "ccc"""",
  )

  //noinspection RedundantDefaultArgument
  def testEscapeTextWhenPastingToString_WithExistingSelection_SelectionEndOutsideLiteral_CaretOutsideLiteral_MultipleSelections(): Unit = doPasteTest(
    s"""text with quote in the end"""",
    s"""val v1 = "aaa ${Start}bbb"$CARET$End + "ccc"
       |val v2 = "aaa ${Start}bbb"$CARET$End + "ccc"
       |val v3 = "aaa ${Start}bbb"$CARET$End + "ccc"
       |""".stripMargin,
    s"""val v1 = "aaa text with quote in the end" + "ccc"
       |val v2 = "aaa text with quote in the end" + "ccc"
       |val v3 = "aaa text with quote in the end" + "ccc"
       |""".stripMargin,
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_SelectionInDifferentStringInConcatenation(): Unit = doPasteTest(
    s""""text with quotes at both sides"""",
    s"""val v = "aaa ${START}bbb" + "ccc$END$CARET ddd"""",
    s"""val v = "aaa \\"text with quotes at both sides\\"$CARET ddd""""
  )

  def testEscapeTextWhenPastingToString_WithExistingSelection_SelectionInDifferentStringInConcatenationOfDifferentKinds_DontDoAnything(): Unit = doPasteTest(
    s""""text with quotes at both sides"""",
    s"""val v = "aaa ${START}bbb" + '''ccc$END$CARET ddd'''""",
    s"""val v = "aaa "text with quotes at both sides"$CARET ddd'''"""
  )
}
