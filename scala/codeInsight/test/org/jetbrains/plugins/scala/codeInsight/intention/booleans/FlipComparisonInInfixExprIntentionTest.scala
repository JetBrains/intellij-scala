package org.jetbrains.plugins.scala.codeInsight.intention.booleans

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class FlipComparisonInInfixExprIntentionTestBase extends ScalaIntentionTestBase {
  override def familyName = ScalaCodeInsightBundle.message("family.name.flip.comparison.in.infix.expression")
}

class FlipComparisonInInfixExprIntentionTest extends FlipComparisonInInfixExprIntentionTestBase {

  def testFlip1(): Unit = {
    val text = s"if (a =$CARET= b) return"
    val resultText = s"if (b =$CARET= a) return"

    doTest(text, resultText)
  }

  def testFlip2(): Unit = {
    val text = s"if (a equal${CARET}s b) return"
    val resultText = s"if (b equal${CARET}s a) return"

    doTest(text, resultText)
  }

  def testFlip3(): Unit = {
    val text = s"if (a >$CARET b) return"
    val resultText = s"if (b <$CARET a) return"

    doTest(text, resultText)
  }

  def testFlip4(): Unit = {
    val text = s"if (a <$CARET b) return"
    val resultText = s"if (b >$CARET a) return"

    doTest(text, resultText)
  }

  def testFlip5(): Unit = {
    val text = s"if (a <=$CARET b) return"
    val resultText = s"if (b >=$CARET a) return"

    doTest(text, resultText)
  }

  def testFlip6(): Unit = {
    val text = s"if (a >=$CARET b) return"
    val resultText = s"if (b <=$CARET a) return"

    doTest(text, resultText)
  }

  def testFlip7(): Unit = {
    val text = s"if (a !$CARET= b) return"
    val resultText = s"if (b !$CARET= a) return"

    doTest(text, resultText)
  }

  def testFlip8(): Unit = {
    val text = s"if (7 <$CARET (7 + 8)) return"
    val resultText = s"if ((7 + 8) >$CARET 7) return"

    doTest(text, resultText)
  }

  def testFlip9(): Unit = {
    val text = s"if ((7 + 8) <$CARET 7) return"
    val resultText = s"if (7 >$CARET (7 + 8)) return"

    doTest(text, resultText)
  }

  def testFlip10(): Unit = {
    val text = s"if (sourceClass == null || (sourceClass e${CARET}q clazz)) return null"
    val resultText = s"if (sourceClass == null || (clazz e${CARET}q sourceClass)) return null"

    doTest(text, resultText)
  }

  def testFlip11(): Unit = {
    val text = s"if (aClass n${CARET}e b) return"
    val resultText = s"if (b n${CARET}e aClass) return"

    doTest(text, resultText)
  }
}

class FlipComparisonInInfixExprIntentionTest_Scala3 extends FlipComparisonInInfixExprIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testFewerBracesAddParentheses(): Unit = {
    val text =
      s"""val x = true
         |val y = 2
         |
         |x &$CARET& locally:
         |  y > 0""".stripMargin
    val resultText =
      s"""val x = true
         |val y = 2
         |
         |(locally:
         |  y > 0) &$CARET& x""".stripMargin

    doTest(text, resultText)
  }

  def testFewerBracesDoNotAddMoreParentheses(): Unit = {
    val text =
      s"""val x = true
         |val y = 2
         |
         |x &$CARET& (locally:
         |  y > 0)""".stripMargin
    val resultText =
      s"""val x = true
         |val y = 2
         |
         |(locally:
         |  y > 0) &$CARET& x""".stripMargin

    doTest(text, resultText)
  }

  def testFewerBracesKeepExistingParenthesesPair(): Unit = {
    val text =
      s"""val x = true
         |val y = 2
         |
         |(locally:
         |  y > 0) &$CARET& x""".stripMargin
    val resultText =
      s"""val x = true
         |val y = 2
         |
         |x &$CARET& (locally:
         |  y > 0)""".stripMargin

    doTest(text, resultText)
  }

  def testFewerBracesWithoutParentheses(): Unit = {
    val text =
      s"""val x = true
         |val y = 2
         |
         |locally:
         |  y > 0 &$CARET& x""".stripMargin
    val resultText =
      s"""val x = true
         |val y = 2
         |
         |locally:
         |  x &$CARET& y > 0""".stripMargin

    doTest(text, resultText)
  }
}
