package org.jetbrains.plugins.scala
package codeInsight.intentions.booleans

import org.jetbrains.plugins.scala.codeInsight.intention.booleans.FlipComparisonInMethodCallExprIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */

class FlipComparisonInMethodCallExprIntentionTest extends ScalaIntentionTestBase {
  def familyName = FlipComparisonInMethodCallExprIntention.familyName

  def testFlip() {
    val text = "if (f.=<caret>=(false)) return"
    val resultText = "if (false.=<caret>=(f)) return"

    doTest(text, resultText)
  }

  def testFlip2() {
    val text = "if (a.equal<caret>s(b)) return"
    val resultText = "if (b.equal<caret>s(a)) return"

    doTest(text, resultText)
  }

  def testFlip3() {
    val text = "if (a.><caret>(b)) return"
    val resultText = "if (b.<<caret>(a)) return"

    doTest(text, resultText)
  }

  def testFlip4() {
    val text = "if (a.<<caret>(b)) return"
    val resultText = "if (b.><caret>(a)) return"

    doTest(text, resultText)
  }

  def testFlip5() {
    val text = "if (a.<=<caret>(b)) return"
    val resultText = "if (b.>=<caret>(a)) return"

    doTest(text, resultText)
  }

  def testFlip6() {
    val text = "if (a.>=<caret>(b)) return"
    val resultText = "if (b.<=<caret>(a)) return"

    doTest(text, resultText)
  }

  def testFlip7() {
    val text = "if (a.!<caret>=(b)) return"
    val resultText = "if (b.!<caret>=(a)) return"

    doTest(text, resultText)
  }

  def testFlip8() {
    val text = "if (7.<<caret>(7 + 8)) return"
    val resultText = "if ((7 + 8).><caret>(7)) return"

    doTest(text, resultText)
  }

  def testFlip9() {
    val text = "if ((7 + 8).<<caret>(7)) return"
    val resultText = "if (7.><caret>(7 + 8)) return"

    doTest(text, resultText)
  }

  def testFlip10() {
    val text = "if (sourceClass == null || sourceClass.e<caret>q(clazz)) return null"
    val resultText = "if (sourceClass == null || clazz.e<caret>q(sourceClass)) return null"

    doTest(text, resultText)
  }

  def testFlip11() {
    val text = "if (aClass.n<caret>e(b)) return"
    val resultText = "if (b.n<caret>e(aClass)) return"

    doTest(text, resultText)
  }

}
