package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.testFramework.EditorTestUtil

/**
  * @author Ksenia.Sautina
  * @since 6/6/12
  */
class InvertIfConditionIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = InvertIfConditionIntention.FamilyName

  def testInvertIf1(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    ${CARET}if (a) b = false
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    ${CARET}if (!a) {
         |
         |    } else {
         |      b = false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf2(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a) {
         |      b = false
         |    }
         |    System.out.println()
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (!a) {
         |
         |    } else {
         |      b = false
         |    }
         |    System.out.println()
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf3(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a == b) {
         |      val c = false
         |    }
         |    println()
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a != b) {
         |
         |    } else {
         |      val c = false
         |    }
         |    println()
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf4(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (!a) b = false
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a) {
         |
         |    } else {
         |      b = false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf5(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) b = false
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) {
         |
         |    } else {
         |      b = false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf6(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (!(a == true)) b = false
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (a == true) {
         |
         |    } else {
         |      b = false
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf7(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if$CARET (false) {
         |
         |    } else {
         |      System.out.print("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if$CARET (true) {
         |      System.out.print("else")
         |    } else {
         |
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf8(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) {
         |      System.out.print("if")
         |    } else {
         |      System.out.print("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) {
         |      System.out.print("else")
         |    } else {
         |      System.out.print("if")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}
