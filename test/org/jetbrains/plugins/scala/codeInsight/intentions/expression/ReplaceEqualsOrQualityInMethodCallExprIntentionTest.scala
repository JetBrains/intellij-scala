package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import codeInsight.intentions.ScalaIntentionTestBase
import codeInsight.intention.expression.ReplaceEqualsOrQualityInMethodCallExprIntention

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

class ReplaceEqualsOrQualityInMethodCallExprIntentionTest extends ScalaIntentionTestBase {
  val familyName = ReplaceEqualsOrQualityInMethodCallExprIntention.familyName

  def testReplaceQuality() {
    val text = "if (a.<caret>==(b)) return"
    val resultText = "if (a.<caret>equals(b)) return"

    doTest(text, resultText)
  }

  def testReplaceQuality2() {
    val text = "if (a.eq<caret>uals(false)) return"
    val resultText = "if (a.<caret>==(false)) return"

    doTest(text, resultText)
  }
}