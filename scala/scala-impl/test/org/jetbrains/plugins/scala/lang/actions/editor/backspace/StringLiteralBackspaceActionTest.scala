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
  private def doTestNotRemove(before: String, after: String): Unit = {
    val settingBefore = ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES
    ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES = false
    try {
      super.doTest(before, after)
    } finally {
      ScalaApplicationSettings.getInstance.INSERT_MULTILINE_QUOTES = settingBefore
    }
  }

  def testNotRemovePairQuotesForMultilineString(): Unit = doTestNotRemove(
    s"""val x = $TripleQuotes$CARET$TripleQuotes""",
    s"""val x = $DoubleQuotes$CARET$TripleQuotes"""
  )

  def testNotRemovePairQuotesForInterpolatedMultilineString(): Unit = doTestNotRemove(
    s"""val x = s$TripleQuotes$CARET$TripleQuotes""",
    s"""val x = s$DoubleQuotes$CARET$TripleQuotes"""
  )

  def testNotRemovePairQuotesForMultilineString_EmptyFile(): Unit = doTestNotRemove(
    s"""$TripleQuotes$CARET$TripleQuotes""",
    s"""$DoubleQuotes$CARET$TripleQuotes"""
  )

  def testNotRemovePairQuotesForInterpolatedMultilineString_EmptyFile(): Unit = doTestNotRemove(
    s"""s$TripleQuotes$CARET$TripleQuotes""",
    s"""s$DoubleQuotes$CARET$TripleQuotes"""
  )

}
