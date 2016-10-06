package org.jetbrains.plugins.scala
package codeInsight.intentions.booleans

import org.jetbrains.plugins.scala.codeInsight.intention.booleans.ReplaceEqualsOrEqualityInMethodCallExprIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

class ReplaceEqualsOrEqualityInMethodCallExprIntentionTest extends ScalaIntentionTestBase {
  val familyName = ReplaceEqualsOrEqualityInMethodCallExprIntention.familyName

  def testReplaceEquality() {
    val text = "if (a.<caret>==(b)) return"
    val resultText = "if (a.<caret>equals(b)) return"

    doTest(text, resultText)
  }

  def testReplaceEquality2() {
    val text = "if (a.eq<caret>uals(false)) return"
    val resultText = "if (a <caret>== false) return"

    doTest(text, resultText)
  }

  def testReplaceEquality2_withSpace() {
    val text = "if (a.<caret>equals( false  )) return"
    val resultText = "if (a <caret>== false) return"

    doTest(text, resultText)
  }

  def testReplaceTuple() = {
    val text = "(true, false).equ<caret>als  (false, false)"
    val resultText = "(true, false) <caret>== (false, false)"

    doTest(text, resultText)
  }

  def testReplaceTuple_2() = {
    val text = "if((true, false).equ<caret>als  (false, false)) return "
    val resultText = "if ((true, false) <caret>== (false, false)) return"

    doTest(text, resultText)
  }

  def testReplaceEqualityInExpression() = {
    val text = "(x.eq<caret>uals(y)).toString"
    val resultText = "(x <caret>== y).toString"

    doTest(text, resultText)
  }

  def testReplaceEqualityInExpression_1() = {
    val text = "x.eq<caret>uals(y).toString"
    val resultText = "(x <caret>== y).toString"

    doTest(text, resultText)
  }
}