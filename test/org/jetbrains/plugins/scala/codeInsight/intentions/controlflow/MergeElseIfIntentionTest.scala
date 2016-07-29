package org.jetbrains.plugins.scala.codeInsight.intentions.controlflow

import org.jetbrains.plugins.scala.codeInsight.intention.controlflow.MergeElseIfIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 6/6/12
 */

class MergeElseIfIntentionTest extends ScalaIntentionTestBase {
  val familyName = MergeElseIfIntention.familyName

  def testMergeElseIf() {
    val text =
      """
        |class MergeElseIf {
        |  def mthd() {
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
    val resultText =
      """
        |class MergeElseIf {
        |  def mthd() {
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

    doTest(text, resultText)
  }

  def testMergeElseIf2() {
    val text =
      """
        |class MergeElseIf {
        |  def mthd() {
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
    val resultText =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9) System.out.println("if1")
        |    el<caret>se if (a == 8)
        |      System.out.println("if2")
        |    else
        |      System.out.println("else")
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testMergeElseIf3() {
    val text =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9) {
        |      System.out.println("if1")
        |    } el<caret>se {
        |      if (a == 8)
        |        System.out.println("if2")
        |      else {
        |        System.out.println("else")
        |      }
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9) {
        |      System.out.println("if1")
        |    } el<caret>se if (a == 8)
        |      System.out.println("if2")
        |    else {
        |      System.out.println("else")
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testMergeElseIf4() {
    val text =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9) {
        |      System.out.println("if1")
        |    } el<caret>se {
        |      if (a == 8)
        |        System.out.println("if2")
        |      else
        |        System.out.println("else")
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9) {
        |      System.out.println("if1")
        |    } el<caret>se if (a == 8)
        |      System.out.println("if2")
        |    else
        |      System.out.println("else")
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testMergeElseIf5() {
    val text =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9)
        |      System.out.println("if1")
        |    else<caret> {
        |      if (a == 8)
        |        System.out.println("if2")
        |      else
        |        System.out.println("else")
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9) System.out.println("if1")
        |    else<caret> if (a == 8)
        |      System.out.println("if2")
        |    else
        |      System.out.println("else")
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testMergeElseIf6() {
    val text =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9)
        |      System.out.println("if1")
        |    else<caret> {
        |      if (a == 8)
        |        System.out.println("if2")
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class MergeElseIf {
        |  def mthd() {
        |    val a: Int = 0
        |    if (a == 9) System.out.println("if1")
        |    else<caret> if (a == 8)
        |      System.out.println("if2")
        |  }
        |}
      """

    doTest(text, resultText)
  }
}