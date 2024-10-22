package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ReplaceEqualsOrEqualityInMethodCallExprIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.replace.equals.or.equality.in.method.call.expression")

  def testReplaceEquality(): Unit = {
    val text = s"if (a.$CARET==(b)) return"
    val resultText = s"if (a.${CARET}equals(b)) return"

    doTest(text, resultText)
  }

  def testReplaceEquality2(): Unit = {
    val text = s"if (a.eq${CARET}uals(false)) return"
    val resultText = s"if (a $CARET== false) return"

    doTest(text, resultText)
  }

  def testReplaceEquality2_withSpace(): Unit = {
    val text = s"if (a.${CARET}equals( false  )) return"
    val resultText = s"if (a $CARET== false) return"

    doTest(text, resultText)
  }

  def testReplaceTuple(): Unit = {
    val text = s"(true, false).equ${CARET}als  (false, false)"
    val resultText = s"(true, false) $CARET== (false, false)"

    doTest(text, resultText)
  }

  def testReplaceTuple_2(): Unit = {
    val text = s"if((true, false).equ${CARET}als  (false, false)) return"
    val resultText = s"if ((true, false) $CARET== (false, false)) return"

    doTest(text, resultText)
  }

  def testReplaceEqualityInExpression(): Unit = {
    val text = s"(x.eq${CARET}uals(y)).toString"
    val resultText = s"(x $CARET== y).toString"

    doTest(text, resultText)
  }

  def testReplaceEqualityInExpression_1(): Unit = {
    val text = s"x.eq${CARET}uals(y).toString"
    val resultText = s"(x $CARET== y).toString"

    doTest(text, resultText)
  }
}
