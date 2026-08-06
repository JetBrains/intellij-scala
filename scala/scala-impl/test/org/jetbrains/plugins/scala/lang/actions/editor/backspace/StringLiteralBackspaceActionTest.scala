package org.jetbrains.plugins.scala
package lang.actions.editor.backspace

import junit.framework.AssertionFailedError
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

class StringLiteralBackspaceActionTest extends ScalaBackspaceHandlerBaseTest {

  protected def doTestWithEmptyLastLine(before: String, after: String): Unit = {
    checkGeneratedTextAfterBackspace(before, after)
    checkGeneratedTextAfterBackspace(before + "\n", after + "\n")
  }

  // Single line
  def testRemovePairQuotesForInterpolatedString_EmptyFile(): Unit = doTestWithEmptyLastLine(
    s"""val x = s"$CARET"""",
    s"""val x = s$CARET"""
  )

  def testRemovePairQuotesForInterpolatedString_MultiCaret_EmptyFile(): Unit = doTestWithEmptyLastLine(
    s"""val x = s"$CARET"
       |val y = s"$CARET"""".stripMargin,
    s"""val x = s$CARET
       |val y = s$CARET""".stripMargin
  )

  // Multiline Remove
  def testRemoveJustInterpolator(): Unit = doTest(
    s"""s$CARET$qqq$qqq""",
    s"""$CARET$qqq$qqq"""
  )

  def testRemoveJustInterpolator_MultiCaret(): Unit = doTest(
    s"""s$CARET$qqq$qqq
       |s$CARET$qqq$qqq
       |s$CARET$qqq$qqq
       |s$CARET$qqq$qqq
       |""".stripMargin,
    s"""$CARET$qqq$qqq
       |$CARET$qqq$qqq
       |$CARET$qqq$qqq
       |$CARET$qqq$qqq
       |""".stripMargin
  )

  def testRemovePairQuotesForMultilineString(): Unit = doTest(
    s"""val x = $qqq$CARET$qqq""",
    s"""val x = $qq$CARET"""
  )

  def testRemovePairQuotesForMultilineString_MultiCaret(): Unit = doTest(
    s"""val x = $qqq$CARET$qqq
       |val y = $qqq$CARET$qqq""".stripMargin,
    s"""val x = $qq$CARET
       |val y = $qq$CARET""".stripMargin
  )

  def testRemovePairQuotesForInterpolatedMultilineString(): Unit = doTest(
    s"""val x = s$qqq$CARET$qqq""",
    s"""val x = s$qq$CARET"""
  )

  def testRemovePairQuotesForInterpolatedMultilineString_MultiCaret(): Unit = doTest(
    s"""val x = s$qqq$CARET$qqq
       |val y = s$qqq$CARET$qqq""".stripMargin,
    s"""val x = s$qq$CARET
       |val y = s$qq$CARET""".stripMargin
  )

  def testRemovePairQuotesForMultilineString_EmptyFile(): Unit = doTestWithEmptyLastLine(
    s"""$qqq$CARET$qqq""",
    s"""$qq$CARET"""
  )

  def testRemovePairQuotesForMultilineString_EmptyFile_MultiCaret(): Unit = doTestWithEmptyLastLine(
    s"""$qqq$CARET$qqq
       |$qqq$CARET$qqq""".stripMargin,
    s"""$qq$CARET
       |$qq$CARET""".stripMargin
  )

  def testRemovePairQuotesForMultilineString_EmptyFile_MultiCaret_Big(): Unit = doTestWithEmptyLastLine(
    s"""$qqq$CARET$qqq
       |$qqq$CARET$qqq
       |$qqq$CARET$qqq
       |$qqq$CARET$qqq
       |$qqq$CARET$qqq
       |$qqq$CARET$qqq
       |$qqq$CARET$qqq
       |$qqq$CARET$qqq
       |$qqq$CARET$qqq""".stripMargin,
    s"""$qq$CARET
       |$qq$CARET
       |$qq$CARET
       |$qq$CARET
       |$qq$CARET
       |$qq$CARET
       |$qq$CARET
       |$qq$CARET
       |$qq$CARET""".stripMargin
  )

  def testRemovePairQuotesForInterpolatedMultilineString_EmptyFile(): Unit = doTestWithEmptyLastLine(
    s"""s$qqq$CARET$qqq""",
    s"""s$qq$CARET"""
  )

  def testRemovePairQuotesForInterpolatedMultilineString_EmptyFile_MultiCaret(): Unit = doTestWithEmptyLastLine(
    s"""s$qqq$CARET$qqq
       |s$qqq$CARET$qqq""".stripMargin,
    s"""s$qq$CARET
       |s$qq$CARET""".stripMargin
  )

  def testRemovePairQuotesForInterpolatedMultilineString_EmptyFile_MultiCaret_Big(): Unit = doTestWithEmptyLastLine(
    s"""s$qqq$CARET$qqq
       |s$qqq$CARET$qqq
       |s$qqq$CARET$qqq
       |s$qqq$CARET$qqq
       |s$qqq$CARET$qqq
       |s$qqq$CARET$qqq
       |s$qqq$CARET$qqq
       |s$qqq$CARET$qqq
       |s$qqq$CARET$qqq""".stripMargin,
    s"""s$qq$CARET
       |s$qq$CARET
       |s$qq$CARET
       |s$qq$CARET
       |s$qq$CARET
       |s$qq$CARET
       |s$qq$CARET
       |s$qq$CARET
       |s$qq$CARET""".stripMargin
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

  def testNotRemovePairQuotesForMultilineString_SettingDisabled(): Unit = doTestWithSettingDisabled(
    s"""val x = $qqq$CARET$qqq""",
    s"""val x = $qq$CARET$qqq"""
  )

  def testNotRemovePairQuotesForInterpolatedMultilineString_SettingDisabled(): Unit = doTestWithSettingDisabled(
    s"""val x = s$qqq$CARET$qqq""",
    s"""val x = s$qq$CARET$qqq"""
  )

  def testNotRemovePairQuotesForMultilineString_EmptyFile_SettingDisabled(): Unit = doTestWithSettingDisabled(
    s"""$qqq$CARET$qqq""",
    s"""$qq$CARET$qqq"""
  )

  def testNotRemovePairQuotesForInterpolatedMultilineString_EmptyFile_SettingDisabled(): Unit = doTestWithSettingDisabled(
    s"""s$qqq$CARET$qqq""",
    s"""s$qq$CARET$qqq"""
  )

  protected def doDataTest(testData: Seq[(String, String)])(preprocess: String => String = identity): Unit =
    testData.zipWithIndex.foreach { case ((before, after), idx) =>
      try {
        doTest(preprocess(before), preprocess(after))
      } catch {
        case failure: AssertionFailedError =>
          System.err.println(s"For test data input $idx:\n`$before`")
          throw failure
      }
    }

  // Multiline Not Remove if string is not empty
  private val testData_NonEmptyString: Seq[(String, String)] = Seq((
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

  def testNotRemoveForMultilineString_NonEmpty(): Unit = doDataTest(testData_NonEmptyString)()

  def testNotRemoveForInterpolatedMultilineString_NonEmpty(): Unit = doDataTest(testData_NonEmptyString)("s" + _)

  def testNotRemoveForInterpolatedRawMultilineString_NonEmpty(): Unit = doDataTest(testData_NonEmptyString)("raw" + _)

  private val testDataCaret_NotInTheMiddle: Seq[(String, String)] = Seq((
    s"""$q$CARET$qq$qqq""",
    s"""$CARET$qq$qqq"""
  ), (
    s"""$qq$CARET$q$qqq""",
    s"""$q$CARET$q$qqq"""
  ), (
    s"""$qqq$q$CARET$qq""",
    s"""$qqq$CARET$qq"""
  ), (
    s"""$qqq$q$CARET$qq
       |val x = 42""".stripMargin,
    s"""$qqq$CARET$qq
       |val x = 42""".stripMargin
  ), (
    s"""$qqq$qq$CARET$q""",
    s"""$qqq$q$CARET$q"""
  ), (
    s"""$qqq$qq$CARET$q
       |val x = 42""".stripMargin,
    s"""$qqq$q$CARET$q
       |val x = 42""".stripMargin
  ))

  def testNotRemoveForMultilineString_IfCaretNotInTheMiddle(): Unit = doDataTest(testDataCaret_NotInTheMiddle)()

  def testNotRemoveForInterpolatedMultilineString_IfCaretNotInTheMiddle(): Unit = doDataTest(testDataCaret_NotInTheMiddle)("s" + _)

  def testNotRemoveForInterpolatedRawMultilineString_IfCaretNotInTheMiddle(): Unit = doDataTest(testDataCaret_NotInTheMiddle)("raw" + _)
}
