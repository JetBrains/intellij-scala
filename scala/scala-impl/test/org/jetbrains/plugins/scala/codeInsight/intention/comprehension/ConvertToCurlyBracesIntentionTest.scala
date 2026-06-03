package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ConvertToCurlyBracesIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = "Convert to curly braces"

  private val resultOfSimple =
    """for {
      |  i <- 1 to 10
      |} yield i + 1""".stripMargin

  def testIntentionBeforeOpenParen(): Unit = doTest(
    s"for$CARET(i <- 1 to 10) yield i + 1",
    resultOfSimple,
  )

  def testIntentionAfterOpenParen(): Unit = doTest(
    s"for(${CARET}i <- 1 to 10) yield i + 1",
    resultOfSimple,
  )

  def testIntentionBeforeLastParen(): Unit = doTest(
    s"for(i <- 1 to 10$CARET) yield i + 1",
    resultOfSimple,
  )

  def testIntentionAfterLastParen(): Unit = doTest(
    s"for(i <- 1 to 10)$CARET yield i + 1",
    resultOfSimple,
  )

  def testIntentionNotAvailableOnForKeyword(): Unit = {
    checkIntentionIsNotAvailable(s"f${CARET}or{i <- 1 to 10} yield i + 1")
  }

  def testIntentionNotAvailableInForBlock(): Unit = {
    checkIntentionIsNotAvailable(s"for(i${CARET}i <- 1 to 10) yield i + 1")
  }

  def testIntentionActionForOnSimpleForStatement(): Unit = {
    val text = s"for$CARET(i <- 1 to 10) yield i + 1"
    val result =
      """for {
        |  i <- 1 to 10
        |} yield i + 1""".stripMargin
    doTest(text, result)
  }

  def testIntentionActionOnLargeForYieldStatement(): Unit = {
    val text = s"""for$CARET(i <- 1 to 10; c <- 'a' to 'e') yield "" + c + i"""
    val result =
      """for {
        |  i <- 1 to 10
        |  c <- 'a' to 'e'
        |} yield "" + c + i""".stripMargin
    doTest(text, result)
  }
}
