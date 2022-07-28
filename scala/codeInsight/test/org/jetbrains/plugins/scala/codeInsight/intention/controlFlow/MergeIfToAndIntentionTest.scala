package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.testFramework.EditorTestUtil

class MergeIfToAndIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

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