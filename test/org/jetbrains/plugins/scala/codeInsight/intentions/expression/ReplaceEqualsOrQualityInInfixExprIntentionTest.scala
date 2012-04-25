package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import codeInsight.intentions.ScalaIntentionTestBase
import codeInsight.intention.expression.ReplaceEqualsOrQualityInInfixExprIntention

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

class ReplaceEqualsOrQualityInInfixExprIntentionTest extends ScalaIntentionTestBase {
  val familyName = ReplaceEqualsOrQualityInInfixExprIntention.familyName

  def testReplaceQuality() {
    val text = "if (a ==<caret> b) return"
    val resultText = "if (a <caret>equals b) return"

    doTest(text, resultText)
  }

  def testReplaceQuality2() {
    val text = "if (a <caret>equals false) return"
    val resultText = "if (a <caret>== false) return"

    doTest(text, resultText)
  }
}
