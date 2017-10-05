package org.jetbrains.plugins.scala
package codeInsight.intentions.controlflow

import org.jetbrains.plugins.scala.codeInsight.intention.controlflow.ReplaceWhileWithDoWhileIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * User: Nikolay.Tropin
 * Date: 4/17/13
 */
class ReplaceWhileWithDoWhileIntentionTest extends ScalaIntentionTestBase {
  def familyName: String = ReplaceWhileWithDoWhileIntention.familyName

  def testReplaceWhile() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    <caret>while(flag) {
        |      //looping
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    if (flag) {
        |      do {
        |        //looping
        |      } while (flag)
        |    }
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceWhile2() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    while<caret>(flag) {
        |      //looping
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    if (flag) {
        |      do {
        |        //looping
        |      } while (flag)
        |    }
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceWhile3() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    while<caret>(flag) print("")
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    if (flag) {
        |      do print("") while (flag)
        |    }
        |  }
        |}
      """
    doTest(text, resultText)
  }
}
