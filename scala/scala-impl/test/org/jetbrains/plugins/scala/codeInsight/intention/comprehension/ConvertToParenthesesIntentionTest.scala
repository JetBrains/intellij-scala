package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ConvertToParenthesesIntentionTest extends ScalaIntentionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  override def familyName = ConvertToParenthesesIntention.FamilyName

  def testIntentionAvailableInSimpleForYieldStatement(): Unit = {
    checkIntentionIsAvailable(s"for$CARET_MARKER{i <- 1 to 10} yield i + 1")
  }

  def testIntentionAvailableInLargeForYieldStatement(): Unit = {
    checkIntentionIsAvailable(
      s"""for$CARET_MARKER{
        |  i <- 1 to 10
        |  c <- 'a' to 'e'
        |} yield "" + c + i""".stripMargin)
  }

  def testIntentionNotAvailableOnForKeyword(): Unit = {
    checkIntentionIsNotAvailable(s"f${CARET_MARKER}or{i <- 1 to 10} yield i + 1")
  }

  def testIntentionNotAvailableInForBlock(): Unit = {
    checkIntentionIsNotAvailable(s"for{${CARET_MARKER}i <- 1 to 10} yield i + 1")
  }

  def testIntentionNotAvailableInSimpleForStatementWithPatentheses(): Unit = {
    checkIntentionIsNotAvailable(s"for$CARET_MARKER(i <- 1 to 10) yield i + 1")
  }

  def testIntentionNotAvailableInLargeForYieldStatementWithPatentheses(): Unit = {
    checkIntentionIsNotAvailable(
      s"""for$CARET_MARKER(
          |  i <- 1 to 10;
          |  c <- 'a' to 'e'
          |) yield "" + c + i""".stripMargin)
  }

  def testIntentionActionForOnSimpleForStatement(): Unit = {
    val text = s"for$CARET_MARKER{i <- 1 to 10} yield i + 1"
    val result = "for (i <- 1 to 10) yield i + 1"
    doTest(text, result)
  }

  def testIntentionActionOnLargeForYieldStatement(): Unit = {
    val text = s"""for$CARET_MARKER{
      |  i <- 1 to 10
      |  c <- 'a' to 'e'
      |} yield "" + c + i""".stripMargin
    val result = """for (i <- 1 to 10; c <- 'a' to 'e') yield "" + c + i"""
    doTest(text, result)
  }
}
