package org.jetbrains.plugins.scala
package lang.actions.editor.backspace

import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class StringLiteralBackspaceActionTest extends ScalaBackspaceHandlerBaseTest {
  private val qq = "\"\""
  private val qqq = "\"\"\""

  // Single line
  def testRemovePairQuotesForInterpolatedString_EmptyFile(): Unit = doTest(
    s"""val x = s"$CARET"""",
    s"""val x = s$CARET"""
  )

  // Multiline Remove
  def testRemovePairQuotesForMultilineString(): Unit = doTest(
    s"""val x = $qqq$CARET$qqq""",
    s"""val x = $qq$CARET"""
  )

  def testRemovePairQuotesForInterpolatedMultilineString(): Unit = doTest(
    s"""val x = s$qqq$CARET$qqq""",
    s"""val x = s$qq$CARET"""
  )

  def testRemovePairQuotesForMultilineString_EmptyFile(): Unit = doTest(
    s"""$qqq$CARET$qqq""",
    s"""$qq$CARET"""
  )

  def testRemovePairQuotesForInterpolatedMultilineString_EmptyFile(): Unit = doTest(
    s"""s$qqq$CARET$qqq""",
    s"""s$qq$CARET"""
  )

  // Multiline Not Remove if setting is disabled
  private def doTestWithSettingDisabled(before: String, after: String): Unit = {
    val settingBefore = ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES
    ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES = false
    try {
      super.doTest(before, after)
    } finally {
      ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES = settingBefore
    }
  }

  def testNotRemovePairQuotesForMultilineString(): Unit = doTestWithSettingDisabled(
    s"""val x = $qqq$CARET$qqq""",
    s"""val x = $qq$CARET$qqq"""
  )

  def testNotRemovePairQuotesForInterpolatedMultilineString(): Unit = doTestWithSettingDisabled(
    s"""val x = s$qqq$CARET$qqq""",
    s"""val x = s$qq$CARET$qqq"""
  )

  def testNotRemovePairQuotesForMultilineString_EmptyFile(): Unit = doTestWithSettingDisabled(
    s"""$qqq$CARET$qqq""",
    s"""$qq$CARET$qqq"""
  )

  def testNotRemovePairQuotesForInterpolatedMultilineString_EmptyFile(): Unit = doTestWithSettingDisabled(
    s"""s$qqq$CARET$qqq""",
    s"""s$qq$CARET$qqq"""
  )

  // Multiline Not Remove if string is not empty
  private val testData: Seq[(String, String)] = Seq((
    s"""$qqq${CARET}some content$qqq""",
    s"""$qq${CARET}some content$qqq"""
  ), (
    s"""$qqq$CARET"some content with 1 quote$qqq""",
    s"""$qq$CARET"some content with 1 quote$qqq"""
  ), (
    s"""$qqq$CARET""some content with 2 quotes$qqq""",
    s"""$qq$CARET""some content with 2 quotes$qqq"""
  ), (
    s"""$qqq$CARET$qqq$qqq""",
    s"""$qq$CARET$qqq$qqq"""
  ), (
    s"""${qqq}some content$qqq$CARET$qqq""",
    s"""${qqq}some content$qq$CARET$qqq"""
  ), (
    s"""$qqq$CARET
       |$qqq""".stripMargin,
    s"""$qq$CARET
       |$qqq""".stripMargin
  ))

  def testNotRemoveForNonEmptyMultilineString(): Unit = testData.foreach { case (before, after) =>
    doTest(before, after)
  }

  def testNotRemoveForNonEmptyInterpMultilineString(): Unit = testData.foreach { case (before, after) =>
    val interpolator = "s"
    doTest(interpolator + before, interpolator + after)
  }

  def testNotRemoveForNonEmptyInterpRawMultilineString(): Unit = testData.foreach { case (before, after) =>
    val interpolator = "raw"
    doTest(interpolator + before, interpolator + after)
  }

}
