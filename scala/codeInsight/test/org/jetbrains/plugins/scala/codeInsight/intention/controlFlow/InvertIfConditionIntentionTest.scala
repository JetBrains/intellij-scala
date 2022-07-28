package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

import com.intellij.testFramework.EditorTestUtil

class InvertIfConditionIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = ScalaCodeInsightBundle.message("family.name.invert.if.condition")

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

  def testInvertIf_NoBraces(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false)
         |      System.out.print("if")
         |    else
         |      System.out.print("else")
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

  def testInvertIf_NoBraces_SameLine(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) System.out.print("if")
         |    else System.out.print("else")
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

  def testInvertIf_NoIfBraces(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) System.out.print("if")
         |    else {
         |      System.out.print("else1")
         |      System.out.print("else2")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) {
         |      System.out.print("else1")
         |      System.out.print("else2")
         |    } else {
         |      System.out.print("if")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_NoElseBraces(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |    else
         |      System.out.print("else")
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    i${CARET}f (true) {
         |      System.out.print("else")
         |    } else {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretAtElse(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    } ${CARET}else {
         |      System.out.print("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (true) {
         |      System.out.print("else")
         |    }$CARET else {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testInvertIf_CaretInsideElse(): Unit = {
    val text =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (false) {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    } el${CARET}se {
         |      System.out.print("else")
         |    }
         |  }
         |}""".stripMargin
    val resultText =
      s"""
         |class X {
         |  def f(a: Boolean, b: Boolean) {
         |    if (true) {
         |      System.out.print("else")
         |    }$CARET else {
         |      System.out.print("if1")
         |      System.out.print("if2")
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }
}
