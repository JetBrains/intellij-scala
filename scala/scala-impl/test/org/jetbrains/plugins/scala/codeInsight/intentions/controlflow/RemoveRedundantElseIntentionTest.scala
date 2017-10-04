package org.jetbrains.plugins.scala.codeInsight.intentions.controlflow

import org.jetbrains.plugins.scala.codeInsight.intention.controlflow.RemoveRedundantElseIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 6/8/12
 */

class RemoveRedundantElseIntentionTest extends ScalaIntentionTestBase {
  val familyName = RemoveRedundantElseIntention.familyName

  def testRemoveElse() {
    val text =
      """
        |class X {
        |  def f(i: Int) {
        |    if (i == 0) {
        |      return
        |    } e<caret>lse {
        |      val j = 0
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(i: Int) {
        |    if (i == 0) {
        |      return
        |    }<caret>
        |    val j = 0
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testRemoveElse2() {
    val text =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0) {
        |      return true
        |    } e<caret>lse {
        |      val j = 0
        |    }
        |    return false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0) {
        |      return true
        |    }<caret>
        |    val j = 0
        |    return false
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testRemoveElse3() {
    val text =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0) {
        |      System.out.println("if")
        |      return true
        |    } e<caret>lse {
        |      System.out.println("else")
        |      val j = 0
        |    }
        |    return false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0) {
        |      System.out.println("if")
        |      return true
        |    }<caret>
        |    System.out.println("else")
        |    val j = 0
        |    return false
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testRemoveElse4() {
    val text =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0) return true
        |    e<caret>lse {
        |      System.out.println("else")
        |      val j = 0
        |    }
        |    return false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0) return true<caret>
        |    System.out.println("else")
        |    val j = 0
        |    return false
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testRemoveElse5() {
    val text =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0)
        |      return true
        |    e<caret>lse
        |      System.out.println("else")
        |    return false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0)
        |      return true<caret>
        |    System.out.println("else")
        |    return false
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testRemoveElse6() {
    val text =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0)
        |      throw new Exception
        |    e<caret>lse
        |      System.out.println("else")
        |    return false
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(i: Int): Boolean = {
        |    if (i == 0)
        |      throw new Exception<caret>
        |    System.out.println("else")
        |    return false
        |  }
        |}
      """

    doTest(text, resultText)
  }
}