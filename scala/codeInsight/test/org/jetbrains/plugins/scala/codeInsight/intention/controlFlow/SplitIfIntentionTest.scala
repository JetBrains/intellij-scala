package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class SplitIfIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.split.if")

  def testSplitIf1(): Unit = {
    val text =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (a &$CARET& b) return
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (${CARET}a)
         |      if (b) return
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf2(): Unit = {
    val text =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (a &$CARET& b) {
         |      return
         |    } else {
         |      System.out.println()
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (${CARET}a)
         |      if (b) {
         |        return
         |      } else {
         |        System.out.println()
         |      }
         |    else {
         |      System.out.println()
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf3(): Unit = {
    val text =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (a &$CARET& b) {
         |      return
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (${CARET}a)
         |      if (b) {
         |        return
         |      }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf4(): Unit = {
    val text =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (a &$CARET& b)
         |      System.out.println("if")
         |    else
         |      System.out.println("else")
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (${CARET}a)
         |      if (b) System.out.println("if")
         |      else System.out.println("else")
         |    else System.out.println("else")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf5(): Unit = {
    val text =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (a &$CARET& b) {
         |      System.out.println("if")
         |    } else
         |      System.out.println("else")
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (${CARET}a)
         |      if (b) {
         |        System.out.println("if")
         |      } else System.out.println("else")
         |    else System.out.println("else")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf6(): Unit = {
    val text =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (a &$CARET& b) System.out.println("if")
         |    else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (${CARET}a)
         |      if (b) System.out.println("if")
         |      else {
         |        System.out.println("else")
         |      }
         |    else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf7(): Unit = {
    val text =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if ((a || b) &$CARET& b) System.out.println("if")
         |    else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (${CARET}a || b)
         |      if (b) System.out.println("if")
         |      else {
         |        System.out.println("else")
         |      }
         |    else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf8(): Unit = {
    val text =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if ((a || b) &$CARET& (b && a)) System.out.println("if")
         |    else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (${CARET}a || b)
         |      if (b && a) System.out.println("if")
         |      else {
         |        System.out.println("else")
         |      }
         |    else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class SplitIfIntentionTest_Scala3 extends intentions.ScalaIntentionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def familyName = ScalaCodeInsightBundle.message("family.name.split.if")

  def testSplitIf1(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if (a &$CARET& b) return
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ${CARET}a then
         |      if b then return
         |""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf2(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if (a &$CARET& b) {
         |      return
         |    } else {
         |      System.out.println()
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ${CARET}a then
         |      if b then
         |        return
         |      else
         |        System.out.println()
         |    else
         |      System.out.println()
         |""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf3(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if (a &$CARET& b) {
         |      return
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ${CARET}a then
         |      if b then
         |        return
         |""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf4(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if (a &$CARET& b)
         |      System.out.println("if")
         |    else
         |      System.out.println("else")
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ${CARET}a then
         |      if b then System.out.println("if")
         |      else System.out.println("else")
         |    else System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf5(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if (a &$CARET& b) {
         |      System.out.println("if")
         |    } else
         |      System.out.println("else")
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ${CARET}a then
         |      if b then
         |        System.out.println("if")
         |      else System.out.println("else")
         |    else System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf6(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if (a &$CARET& b) System.out.println("if")
         |    else {
         |      System.out.println("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ${CARET}a then
         |      if b then System.out.println("if")
         |      else
         |        System.out.println("else")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf7(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ((a || b) &$CARET& b) System.out.println("if")
         |    else {
         |      System.out.println("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ${CARET}a || b then
         |      if b then System.out.println("if")
         |      else
         |        System.out.println("else")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testSplitIf8(): Unit = {
    val text =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ((a || b) &$CARET& (b && a)) System.out.println("if")
         |    else {
         |      System.out.println("else")
         |    }
         |""".stripMargin
    val resultText =
      s"""class X:
         |  def f(a: Boolean, b: Boolean) =
         |    if ${CARET}a || b then
         |      if b && a then System.out.println("if")
         |      else
         |        System.out.println("else")
         |    else
         |      System.out.println("else")
         |""".stripMargin

    doTest(text, resultText)
  }
}
