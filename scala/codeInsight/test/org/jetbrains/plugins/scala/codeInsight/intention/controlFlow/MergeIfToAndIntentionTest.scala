package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class MergeIfToAndIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.merge.nested.ifs.to.anded.condition")

  def testMergeIfToAnd1(): Unit = {
    val text =
      s"""
         |class MergeIfToAnd {
         |  def mthd() {
         |    val a: Int = 0
         |    i${CARET}f (a == 9) {
         |      if (a == 7) {
         |        System.out.println("if")
         |      }
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class MergeIfToAnd {
         |  def mthd() {
         |    val a: Int = 0
         |    i${CARET}f (a == 9 && a == 7) {
         |      System.out.println("if")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToAnd2(): Unit = {
    val text =
      s"""
         |class MergeIfToAnd {
         |  def mthd() {
         |    val a: Int = 0
         |    i${CARET}f (a == 9)
         |      if (a == 7)
         |        System.out.println("if")
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class MergeIfToAnd {
         |  def mthd() {
         |    val a: Int = 0
         |    i${CARET}f (a == 9 && a == 7) System.out.println("if")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToAnd3(): Unit = {
    val text =
      s"""
         |class MergeIfToAnd {
         |  def mthd() {
         |    val a: Int = 0
         |    i${CARET}f (a == 9) {
         |      if (a == 7)
         |        System.out.println("if")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class MergeIfToAnd {
         |  def mthd() {
         |    val a: Int = 0
         |    i${CARET}f (a == 9 && a == 7) System.out.println("if")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToAnd4(): Unit = {
    val text =
      s"""
         |class MergeIfToAnd {
         |  def mthd() {
         |    val a: Int = 0
         |    i${CARET}f (a == 9)
         |      if (a == 7) {
         |        System.out.println("if")
         |      }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class MergeIfToAnd {
         |  def mthd() {
         |    val a: Int = 0
         |    i${CARET}f (a == 9 && a == 7) {
         |      System.out.println("if")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class MergeIfToAndIntentionTest_Scala3 extends intentions.ScalaIntentionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def familyName = ScalaCodeInsightBundle.message("family.name.merge.nested.ifs.to.anded.condition")

  def testMergeIfToAnd1(): Unit = {
    val text =
      s"""class MergeIfToAnd:
         |  def mthd() =
         |    val a: Int = 0
         |    i${CARET}f (a == 9) {
         |      if (a == 7) {
         |        System.out.println("if")
         |      }
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeIfToAnd:
         |  def mthd() =
         |    val a: Int = 0
         |    i${CARET}f a == 9 && a == 7 then
         |      System.out.println("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToAnd2(): Unit = {
    val text =
      s"""class MergeIfToAnd:
         |  def mthd() =
         |    val a: Int = 0
         |    i${CARET}f (a == 9)
         |      if (a == 7)
         |        System.out.println("if")
         |""".stripMargin
    val resultText =
      s"""class MergeIfToAnd:
         |  def mthd() =
         |    val a: Int = 0
         |    i${CARET}f a == 9 && a == 7 then System.out.println("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToAnd3(): Unit = {
    val text =
      s"""class MergeIfToAnd:
         |  def mthd() =
         |    val a: Int = 0
         |    i${CARET}f (a == 9) {
         |      if (a == 7)
         |        System.out.println("if")
         |    }
         |""".stripMargin
    val resultText =
      s"""class MergeIfToAnd:
         |  def mthd() =
         |    val a: Int = 0
         |    i${CARET}f a == 9 && a == 7 then System.out.println("if")
         |""".stripMargin

    doTest(text, resultText)
  }

  def testMergeIfToAnd4(): Unit = {
    val text =
      s"""class MergeIfToAnd:
         |  def mthd() =
         |    val a: Int = 0
         |    i${CARET}f (a == 9)
         |      if (a == 7) {
         |        System.out.println("if")
         |      }
         |""".stripMargin
    val resultText =
      s"""class MergeIfToAnd:
         |  def mthd() =
         |    val a: Int = 0
         |    i${CARET}f a == 9 && a == 7 then
         |      System.out.println("if")
         |""".stripMargin

    doTest(text, resultText)
  }
}
