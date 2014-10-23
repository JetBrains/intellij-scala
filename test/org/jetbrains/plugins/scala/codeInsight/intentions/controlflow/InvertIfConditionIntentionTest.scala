package org.jetbrains.plugins.scala.codeInsight.intentions.controlflow

import org.jetbrains.plugins.scala.codeInsight.intention.controlflow.InvertIfConditionIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 6/6/12
 */

class InvertIfConditionIntentionTest extends ScalaIntentionTestBase {
  val familyName = InvertIfConditionIntention.familyName

  def testInvertIf() {
    val text =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    <caret>if (a) b = false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    <caret>if (!a) {
        |
        |    } else {
        |      b = false
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testInvertIf2() {
    val text =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (a) {
        |      b = false
        |    }
        |    System.out.println()
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (!a) {
        |
        |    } else {
        |      b = false
        |    }
        |    System.out.println()
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testInvertIf3() {
    val text =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (a == b) {
        |      val c = false
        |    }
        |    println()
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (a != b) {
        |
        |    } else {
        |      val c = false
        |    }
        |    println()
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testInvertIf4() {
    val text =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (!a) b = false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (a) {
        |
        |    } else {
        |      b = false
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testInvertIf5() {
    val text =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (true) b = false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (false) {
        |
        |    } else {
        |      b = false
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testInvertIf6() {
    val text =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (!(a == true)) b = false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (a == true) {
        |
        |    } else {
        |      b = false
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testInvertIf7() {
    val text =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    if<caret> (false) {
        |
        |    } else {
        |      System.out.print("else")
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    if<caret> (true) {
        |      System.out.print("else")
        |    } else {
        |
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testInvertIf8() {
    val text =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (false) {
        |      System.out.print("if")
        |    } else {
        |      System.out.print("else")
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Boolean, b: Boolean) {
        |    i<caret>f (true) {
        |      System.out.print("else")
        |    } else {
        |      System.out.print("if")
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }
}
