package org.jetbrains.plugins.scala
package lang.actions.editor.backspace

import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class StringLiteralBackspaceActionTest extends ScalaBackspaceHandlerBaseTest {
  private val DoubleQuotes = "\"\""
  private val TripleQuotes = "\"\"\""

  // Single line
  def testRemovePairQuotesForInterpolatedString_EmptyFile(): Unit = doTest(
    s"""val x = s"$CARET"""",
    s"""val x = s$CARET"""
  )

  // Multiline Remove
  def testRemovePairQuotesForMultilineString(): Unit = doTest(
    s"""val x = $TripleQuotes$CARET$TripleQuotes""",
    s"""val x = $DoubleQuotes$CARET"""
  )

  def testRemovePairQuotesForInterpolatedMultilineString(): Unit = doTest(
    s"""val x = s$TripleQuotes$CARET$TripleQuotes""",
    s"""val x = s$DoubleQuotes$CARET"""
  )

  def testRemovePairQuotesForMultilineString_EmptyFile(): Unit = doTest(
    s"""$TripleQuotes$CARET$TripleQuotes""",
    s"""$DoubleQuotes$CARET"""
  )

  def testRemovePairQuotesForInterpolatedMultilineString_EmptyFile(): Unit = doTest(
    s"""s$TripleQuotes$CARET$TripleQuotes""",
    s"""s$DoubleQuotes$CARET"""
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
    s"""val x = $TripleQuotes$CARET$TripleQuotes""",
    s"""val x = $DoubleQuotes$CARET$TripleQuotes"""
  )

  def testNotRemovePairQuotesForInterpolatedMultilineString(): Unit = doTestWithSettingDisabled(
    s"""val x = s$TripleQuotes$CARET$TripleQuotes""",
    s"""val x = s$DoubleQuotes$CARET$TripleQuotes"""
  )

  def testNotRemovePairQuotesForMultilineString_EmptyFile(): Unit = doTestWithSettingDisabled(
    s"""$TripleQuotes$CARET$TripleQuotes""",
    s"""$DoubleQuotes$CARET$TripleQuotes"""
  )

  def testNotRemovePairQuotesForInterpolatedMultilineString_EmptyFile(): Unit = doTestWithSettingDisabled(
    s"""s$TripleQuotes$CARET$TripleQuotes""",
    s"""s$DoubleQuotes$CARET$TripleQuotes"""
  )

  // Multiline Not Remove if string is not empty
  private val testData: Seq[(String, String)] = Seq((
    s"""$TripleQuotes${CARET}some content$TripleQuotes""",
    s"""$DoubleQuotes${CARET}some content$TripleQuotes"""
  ), (
    s"""$TripleQuotes$CARET"some content with 1 quote$TripleQuotes""",
    s"""$DoubleQuotes$CARET"some content with 1 quote$TripleQuotes"""
  ), (
    s"""$TripleQuotes$CARET""some content with 2 quotes$TripleQuotes""",
    s"""$DoubleQuotes$CARET""some content with 2 quotes$TripleQuotes"""
  ), (
    s"""$TripleQuotes$CARET$TripleQuotes$TripleQuotes""",
    s"""$DoubleQuotes$CARET$TripleQuotes$TripleQuotes"""
  ), (
    s"""${TripleQuotes}some content$TripleQuotes$CARET$TripleQuotes""",
    s"""${TripleQuotes}some content$DoubleQuotes$CARET$TripleQuotes"""
  ), (
    s"""$TripleQuotes$CARET
       |$TripleQuotes""".stripMargin,
    s"""$DoubleQuotes$CARET
       |$TripleQuotes""".stripMargin
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
