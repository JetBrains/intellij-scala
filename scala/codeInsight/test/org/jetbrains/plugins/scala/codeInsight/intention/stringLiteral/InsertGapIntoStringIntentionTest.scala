package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class InsertGapIntoStringIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = ScalaCodeInsightBundle.message("family.name.insert.gap")

  override protected def formatIntentionResultBeforeChecking: Boolean = false

  def testInsertGap_StringLiteral_InTheMiddle(): Unit =
    doTest(
      s""" "12${CARET}34" """,
      s""" "12" + $CARET + "34" """,
    )

  def testInsertGap_StringLiteral_InTheBeginning(): Unit =
    doTest(
      s""" "${CARET}1234" """,
      s""" "" + ${CARET} + "1234" """,
    )

  def testInsertGap_StringLiteral_InTheEnd(): Unit =
    doTest(
      s""" "1234$CARET" """,
      s""" "1234" + $CARET + "" """,
    )

  def testInsertGap_StringLiteral_Before_NotAvailable(): Unit =
    checkIntentionIsNotAvailable(
      s""" $CARET"1234" """
    )

  def testInsertGap_StringLiteral_After_NotAvailable(): Unit =
    checkIntentionIsNotAvailable(
      s""" "1234"$CARET """
    )


  def testInsertGap_MultilineStringLiteral_InTheMiddle(): Unit =
    doTest(
      s""" \"\"\"12${CARET}34\"\"\" """,
      s""" \"\"\"12\"\"\" + $CARET + \"\"\"34\"\"\" """,
    )

  def testInsertGap_MultilineStringLiteral_InTheBeginning(): Unit =
    doTest(
      s""" \"\"\"${CARET}1234\"\"\" """,
      s""" \"\"\"\"\"\" + $CARET + \"\"\"1234\"\"\" """,
    )

  def testInsertGap_MultilineStringLiteral_InTheEnd(): Unit =
    doTest(
      s""" \"\"\"1234$CARET\"\"\" """,
      s""" \"\"\"1234\"\"\" + $CARET + \"\"\"\"\"\" """,
    )

  def testInsertGap_MultilineStringLiteral_Before_NotAvailable(): Unit =
    checkIntentionIsNotAvailable(
      s""" $CARET\"\"\"1234\"\"\" """
    )

  def testInsertGap_MultilineStringLiteral_After_NotAvailable(): Unit =
    checkIntentionIsNotAvailable(
      s""" \"\"\"1234\"\"\"$CARET """
    )

  def testInsertGap_MultilineStringLiteral_InTheMiddleOfStartQuotes_NotAvailable(): Unit =
    checkIntentionIsNotAvailable(
      s""" ""$CARET\"1234\"\"\" """
    )

  def testInsertGap_MultilineStringLiteral_InTheMiddleOfEndQuotes_NotAvailable(): Unit =
    checkIntentionIsNotAvailable(
      s""" \"\"\"1234"$CARET"\" """
    )
}