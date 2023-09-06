package org.jetbrains.plugins.scala.codeInsight.intention.booleans

import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class FlipComparisonInMethodCallExprIntentionTestBase extends ScalaIntentionTestBase {
  override def familyName = ScalaCodeInsightBundle.message("family.name.flip.comparison.in.method.call.expression")
}

final class FlipComparisonInMethodCallExprIntentionTest extends FlipComparisonInMethodCallExprIntentionTestBase {
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

final class FlipComparisonInMethodCallExprIntentionTest_FewerBraces extends FlipComparisonInMethodCallExprIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testFlip1(): Unit = {
    val text =
      s"""if (f.=$CARET= :
         |  false) return""".stripMargin
    val resultText = s"if (false.=$CARET=(f)) return"

    doTest(text, resultText)
  }

  def testFlip2(): Unit = {
    val text =
      s"""if (a.equal${CARET}s:
         |  b) return""".stripMargin
    val resultText = s"if (b.equal${CARET}s(a)) return"

    doTest(text, resultText)
  }

  def testFlip3(): Unit = {
    val text =
      s"""if (7.<$CARET :
         |  7 + 8) return""".stripMargin
    val resultText = s"if ((7 + 8).>$CARET(7)) return"

    doTest(text, resultText)
  }

  def testFlip4(): Unit = {
    val text =
      s"""if ((7 + 8).<$CARET :
         |  7) return""".stripMargin
    val resultText = s"if (7.>$CARET(7 + 8)) return"

    doTest(text, resultText)
  }

  def testFlip5(): Unit = {
    val text =
      s"""if (sourceClass == null || sourceClass.e${CARET}q:
         |  clazz) return null""".stripMargin
    val resultText = s"if (sourceClass == null || clazz.e${CARET}q(sourceClass)) return null"

    doTest(text, resultText)
  }

  def testFlip6(): Unit = {
    val text =
      s"""if (sourceClass == null || sourceClass.e${CARET}q:
         |  val cls =
         |    if b then clazz1
         |    else clazz2
         |  cls) return null""".stripMargin
    val resultText =
      s"""if (sourceClass == null || {
         |  val cls =
         |    if b then clazz1
         |    else clazz2
         |  cls
         |}.e${CARET}q(sourceClass)) return null""".stripMargin

    doTest(text, resultText)
  }
}
