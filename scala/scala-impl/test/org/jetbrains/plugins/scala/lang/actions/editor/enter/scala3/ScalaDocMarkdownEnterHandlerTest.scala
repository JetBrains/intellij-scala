package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

class ScalaDocMarkdownEnterHandlerTest extends DoEditorStateTestOps {
  private def doEnterTestWithAndWithoutTextAfterCaret(before: String, after: String): Unit = {
    super.doEnterTest(before, after)

    def replaceCaret(s: String) = s.replace(CARET, CARET + "abc")
    doEnterTest(replaceCaret(before), replaceCaret(after))
  }

  def testUnorderedList(): Unit =
    doEnterTest(
      s"""
         |/**
         | * - a list item$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * - a list item
         | * - $CARET
         | */
         |""".stripMargin,
    )

  def testNestedUnorderedList(): Unit =
    doEnterTest(
      s"""
         |/**
         | * - a list item
         | *   - a sublist$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * - a list item
         | *   - a sublist
         | *   - $CARET
         | */
         |""".stripMargin,
    )

  def testUnorderedListWithAfterText(): Unit =
    doEnterTest(
      s"""
         |/**
         | * - a list item${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * - a list item
         | *   ${CARET}abc
         | */
         |""".stripMargin,
    )

  def testNestedUnorderedListWithAfterText(): Unit =
    doEnterTest(
      s"""
         |/**
         | * - a list item
         | *   - a sublist${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * - a list item
         | *   - a sublist
         | *     ${CARET}abc
         | */
         |""".stripMargin,
    )

  def testOrderedList(): Unit =
    doEnterTest(
      s"""
         |/**
         | * 1. a list item$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * 1. a list item
         | * 2. $CARET
         | */
         |""".stripMargin,
    )

  def testNestedOrderedList(): Unit =
    doEnterTest(
      s"""
         |/**
         | * 1. a list item
         | *   2. a sublist$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * 1. a list item
         | *   2. a sublist
         | *   3. $CARET
         | */
         |""".stripMargin,
    )

  def testOrderedListWithAfterText(): Unit =
    doEnterTest(
      s"""
         |/**
         | * 1. a list item${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * 1. a list item
         | *    ${CARET}abc
         | */
         |""".stripMargin,
    )

  def testNestedOrderedListWithAfterText(): Unit =
    doEnterTest(
      s"""
         |/**
         | * 1. a list item
         | *   2. a sublist${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * 1. a list item
         | *   2. a sublist
         | *      ${CARET}abc
         | */
         |""".stripMargin,
    )

  def testMaxNumberOrderedList(): Unit =
    doEnterTest(
      s"""
         |/**
         | * > 2147483647. suspicious xD$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * > 2147483647. suspicious xD
         | * > $CARET
         | */
         |""".stripMargin,
    )

  def testMaxNumberOrderedListWithAfterText(): Unit =
    doEnterTest(
      s"""
         |/**
         | * > 2147483647. suspicious xD${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * > 2147483647. suspicious xD
         | * >             ${CARET}abc
         | */
         |""".stripMargin,
    )

  def testQuote(): Unit =
    doEnterTestWithAndWithoutTextAfterCaret(
      s"""
         |/**
         | * > quoted text$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * > quoted text
         | * > $CARET
         | */
         |""".stripMargin,
    )

  def testDoubleQuoteWithSpace(): Unit =
    doEnterTestWithAndWithoutTextAfterCaret(
      s"""
         |/**
         | * > > quoted text$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * > > quoted text
         | * > > $CARET
         | */
         |""".stripMargin,
    )

  def testDoubleQuoteWithoutSpace(): Unit =
    doEnterTestWithAndWithoutTextAfterCaret(
      s"""
         |/**
         | * >> quoted text$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * >> quoted text
         | * >> $CARET
         | */
         |""".stripMargin,
    )
}
