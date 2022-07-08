package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.testFramework.EditorTestUtil

class SplitElseIfIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = ScalaCodeInsightBundle.message("family.name.split.else.if")

  def testSplitElseIf1(): Unit = {
    val text =
      s"""class SplitElseIf {
         |  def mthd {
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se if (a == 8) {
         |      System.out.println("if2")
         |    } else {
         |      System.out.println("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""class SplitElseIf {
         |  def mthd {
         |    val a: Int = 0
         |    if (a == 9) {
         |      System.out.println("if1")
         |    } el${CARET}se {
         |      if (a == 8) {
         |        System.out.println("if2")
         |      } else {
         |        System.out.println("else")
         |      }
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitElseIf2(): Unit = {
    val text =
      s"""class SplitElseIf {
         |  def mthd {
         |    val a: Int = 0
         |    if (a == 9)
         |      System.out.println("if1")
         |    el${CARET}se if (a == 8)
         |      System.out.println("if2")
         |    else
         |      System.out.println("else")
         |  }
         |}""".stripMargin
    val resultText =
      s"""class SplitElseIf {
         |  def mthd {
         |    val a: Int = 0
         |    if (a == 9) System.out.println("if1")
         |    el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |      else
         |        System.out.println("else")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitElseIf3(): Unit = {
    val text =
      s"""class SplitElseIf {
         |  def mthd {
         |    val a: Int = 0
         |    if (a == 9)
         |      System.out.println("if1")
         |    el${CARET}se if (a == 8)
         |      System.out.println("if2")
         |  }
         |}""".stripMargin
    val resultText =
      s"""class SplitElseIf {
         |  def mthd {
         |    val a: Int = 0
         |    if (a == 9) System.out.println("if1")
         |    el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testSplitElseIf4(): Unit = {
    val text =
      s"""class SplitElseIf {
         |  def mthd {
         |    val a: Int = 0
         |    if (a == 9)
         |      System.out.println("if1")
         |    el${CARET}se
         |      if (a == 8)
         |        System.out.println("if2")
         |  }
         |}""".stripMargin
    val resultText =
      s"""class SplitElseIf {
         |  def mthd {
         |    val a: Int = 0
         |    if (a == 9) System.out.println("if1")
         |    el${CARET}se {
         |      if (a == 8)
         |        System.out.println("if2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}