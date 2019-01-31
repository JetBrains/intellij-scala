package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.EditorTestUtil

/**
 * @author Ksenia.Sautina
 * @since 4/20/12
 */
class FlipComparisonInMethodCallExprIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = FlipComparisonInMethodCallExprIntention.FamilyName

  def testFlip1(): Unit = {
    val text = s"if (f.=$CARET=(false)) return"
    val resultText = s"if (false.=$CARET=(f)) return"

    doTest(text, resultText)
  }

  def testFlip2(): Unit = {
    val text = s"if (a.equal${CARET}s(b)) return"
    val resultText = s"if (b.equal${CARET}s(a)) return"

    doTest(text, resultText)
  }

  def testFlip3(): Unit = {
    val text = s"if (a.>$CARET(b)) return"
    val resultText = s"if (b.<$CARET(a)) return"

    doTest(text, resultText)
  }

  def testFlip4(): Unit = {
    val text = s"if (a.<$CARET(b)) return"
    val resultText = s"if (b.>$CARET(a)) return"

    doTest(text, resultText)
  }

  def testFlip5(): Unit = {
    val text = s"if (a.<=$CARET(b)) return"
    val resultText = s"if (b.>=$CARET(a)) return"

    doTest(text, resultText)
  }

  def testFlip6(): Unit = {
    val text = s"if (a.>=$CARET(b)) return"
    val resultText = s"if (b.<=$CARET(a)) return"

    doTest(text, resultText)
  }

  def testFlip7(): Unit = {
    val text = s"if (a.!$CARET=(b)) return"
    val resultText = s"if (b.!$CARET=(a)) return"

    doTest(text, resultText)
  }

  def testFlip8(): Unit = {
    val text = s"if (7.<$CARET(7 + 8)) return"
    val resultText = s"if ((7 + 8).>$CARET(7)) return"

    doTest(text, resultText)
  }

  def testFlip9(): Unit = {
    val text = s"if ((7 + 8).<$CARET(7)) return"
    val resultText = s"if (7.>$CARET(7 + 8)) return"

    doTest(text, resultText)
  }

  def testFlip10(): Unit = {
    val text = s"if (sourceClass == null || sourceClass.e${CARET}q(clazz)) return null"
    val resultText = s"if (sourceClass == null || clazz.e${CARET}q(sourceClass)) return null"

    doTest(text, resultText)
  }

  def testFlip11(): Unit = {
    val text = s"if (aClass.n${CARET}e(b)) return"
    val resultText = s"if (b.n${CARET}e(aClass)) return"

    doTest(text, resultText)
  }

}
