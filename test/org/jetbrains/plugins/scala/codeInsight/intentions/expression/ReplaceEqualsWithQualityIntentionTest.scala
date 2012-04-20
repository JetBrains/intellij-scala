package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import codeInsight.intentions.ScalaIntentionTestBase
import codeInsight.intention.expression.{ReplaceEqualsWithQualityIntention, ReplaceQualityWithEqualsIntention}

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

class ReplaceEqualsWithQualityIntentionTest extends ScalaIntentionTestBase {
  val familyName = ReplaceEqualsWithQualityIntention.familyName

  def testReplaceQuality() {
    val text = "if (a equ<caret>als b) return"
    val resultText = "if (a <caret>== b) return"

    doTest(text, resultText)
  }

  def testReplaceQuality2() {
    val text = "if (a.eq<caret>uals(false)) return"
    val resultText = "if (a.<caret>==(false)) return"

    doTest(text, resultText)
  }
}