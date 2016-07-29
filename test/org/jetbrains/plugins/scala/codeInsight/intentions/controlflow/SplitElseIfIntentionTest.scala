package org.jetbrains.plugins.scala.codeInsight.intentions.controlflow

import org.jetbrains.plugins.scala.codeInsight.intention.controlflow.SplitElseIfIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 6/6/12
 */

class SplitElseIfIntentionTest extends ScalaIntentionTestBase {
  val familyName = SplitElseIfIntention.familyName

  def testSplitElseIf() {
    val text =
      """
        |class SplitElseIf {
        |  def mthd {
        |    val a: Int = 0
        |    if (a == 9) {
        |      System.out.println("if1")
        |    } el<caret>se if (a == 8) {
        |      System.out.println("if2")
        |    } else {
        |      System.out.println("else")
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class SplitElseIf {
        |  def mthd {
        |    val a: Int = 0
        |    if (a == 9) {
        |      System.out.println("if1")
        |    } el<caret>se {
        |      if (a == 8) {
        |        System.out.println("if2")
        |      } else {
        |        System.out.println("else")
        |      }
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testSplitElseIf2() {
    val text =
      """
        |class SplitElseIf {
        |  def mthd {
        |    val a: Int = 0
        |    if (a == 9)
        |      System.out.println("if1")
        |    el<caret>se if (a == 8)
        |      System.out.println("if2")
        |    else
        |      System.out.println("else")
        |  }
        |}
      """
    val resultText =
      """
        |class SplitElseIf {
        |  def mthd {
        |    val a: Int = 0
        |    if (a == 9) System.out.println("if1")
        |    el<caret>se {
        |      if (a == 8)
        |        System.out.println("if2")
        |      else
        |        System.out.println("else")
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testSplitElseIf3() {
    val text =
      """
        |class SplitElseIf {
        |  def mthd {
        |    val a: Int = 0
        |    if (a == 9)
        |      System.out.println("if1")
        |    el<caret>se if (a == 8)
        |      System.out.println("if2")
        |  }
        |}
      """
    val resultText =
      """
        |class SplitElseIf {
        |  def mthd {
        |    val a: Int = 0
        |    if (a == 9) System.out.println("if1")
        |    el<caret>se {
        |      if (a == 8)
        |        System.out.println("if2")
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testSplitElseIf4() {
    val text =
      """
        |class SplitElseIf {
        |  def mthd {
        |    val a: Int = 0
        |    if (a == 9)
        |      System.out.println("if1")
        |    el<caret>se
        |      if (a == 8)
        |        System.out.println("if2")
        |  }
        |}
      """
    val resultText =
      """
        |class SplitElseIf {
        |  def mthd {
        |    val a: Int = 0
        |    if (a == 9) System.out.println("if1")
        |    el<caret>se {
        |      if (a == 8)
        |        System.out.println("if2")
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }
}