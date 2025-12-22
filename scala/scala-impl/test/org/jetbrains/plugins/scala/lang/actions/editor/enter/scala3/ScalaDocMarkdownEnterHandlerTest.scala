package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

class ScalaDocMarkdownEnterHandlerTest extends DoEditorStateTestOps {

  private def doMyEnterTest(before: String, after: String, afterOther: String*): Unit = {
    def replaceInStr(replace: String => String): Unit = {
      val rBefore = replace(before)
      val rAfter = replace(after)
      val rAfterOther = afterOther.map(replace)

      if ((rBefore, rAfter, rAfterOther) != (before, after, afterOther)) {
        super.doEnterTest(rBefore, rAfter, rAfterOther: _*)
      }
    }

    super.doEnterTest(before, after, afterOther: _*)
    replaceInStr(_.replace("-", "+"))
    replaceInStr(_.replace("-", "*"))
    replaceInStr(_.replace(".", ")"))
  }

  private def doMyEnterTestWithAndWithoutTextAfterCaret(before: String, after: String): Unit = {
    doMyEnterTest(before, after)

    def replaceCaret(s: String) = s.replace(CARET, CARET + "abc")
    doMyEnterTest(replaceCaret(before), replaceCaret(after))
  }

  def testUnorderedList(): Unit =
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTest(
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
    doMyEnterTestWithAndWithoutTextAfterCaret(
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
    doMyEnterTestWithAndWithoutTextAfterCaret(
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
    doMyEnterTestWithAndWithoutTextAfterCaret(
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

  def testEnterAfterHeader(): Unit =
    doEnterTest(
      s"""
         |/**
         | * Header
         | * ------$CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * Header
         | * ------
         | * $CARET
         | */
         |""".stripMargin,
    )

  def testEnterAfterEmptyUnorderedList(): Unit =
    doMyEnterTest(
      s"""
         |/**
         | * - $CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | * -
         | * $CARET
         | */
         |""".stripMargin,
    )
}
