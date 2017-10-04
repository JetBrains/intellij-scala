package org.jetbrains.plugins.scala
package codeInsight.intentions.booleans

import org.jetbrains.plugins.scala.codeInsight.intention.booleans.ReplaceEqualsOrEqualityInInfixExprIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

class ReplaceEqualsOrEqualityInInfixExprIntentionTest extends ScalaIntentionTestBase {
  val familyName = ReplaceEqualsOrEqualityInInfixExprIntention.familyName

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
