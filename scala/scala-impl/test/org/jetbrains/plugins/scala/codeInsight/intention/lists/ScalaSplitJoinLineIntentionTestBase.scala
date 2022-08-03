package org.jetbrains.plugins.scala.codeInsight.intention.lists

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.fail
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_3_Latest,
))
abstract class ScalaSplitJoinLineIntentionTestBase extends ScalaIntentionTestBase {
  protected def testType: SplitJoinTestType

  protected def intentionText: String

  protected def doTest(singleLineText: String, multiLineText: String, listStartChar: Char): Unit = {
    val text = if (testType.isJoin) multiLineText else singleLineText
    val resultText = if (testType.isJoin) singleLineText else multiLineText
    val expectedIntentionText = Some(intentionText)

    super.doTest(
      placeCaretAtLastOccurrence(text, listStartChar),
      placeCaretAtLastOccurrence(resultText, listStartChar),
      expectedIntentionText
    )
  }

  private def placeCaretAtLastOccurrence(text: String, ch: Char): String = {
    if (text.contains(CARET)) return text

    val idx = text.lastIndexOf(ch)
    if (idx < 0) fail(s"Couldn't find `$ch` in text:\n$text")

    text.patch(idx, CARET, 0)
  }
}
