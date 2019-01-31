package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.EditorTestUtil

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */
class ReplaceEqualsOrEqualityInMethodCallExprIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = ReplaceEqualsOrEqualityInMethodCallExprIntention.FamilyName

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
    val text = s"if((true, false).equ${CARET}als  (false, false)) return "
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