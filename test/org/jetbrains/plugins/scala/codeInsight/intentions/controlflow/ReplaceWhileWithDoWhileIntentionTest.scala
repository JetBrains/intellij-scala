package org.jetbrains.plugins.scala
package codeInsight.intentions.controlflow

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.codeInsight.intention.controlflow.ReplaceWhileWithDoWhileIntention

/**
 * User: Nikolay.Tropin
 * Date: 4/17/13
 */
class ReplaceWhileWithDoWhileIntentionTest extends ScalaIntentionTestBase{
  def familyName: String = ReplaceWhileWithDoWhileIntention.familyName

  def testReplaceWhile() {
    val text  = """
                  |class X {
                  |  val flag: Boolean
                  |
                  |  def f {
                  |    <caret>while(flag) {
                  |      //looping
                  |    }
                  |  }
                  |}
                """.stripMargin.replace("\r", "").trim
    val resultText = """
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
                     """.stripMargin.replace("\r", "").trim
    doTest(text, resultText)
  }

  def testReplaceWhile2() {
    val text  = """
                  |class X {
                  |  val flag: Boolean
                  |
                  |  def f {
                  |    while<caret>(flag) {
                  |      //looping
                  |    }
                  |  }
                  |}
                """.stripMargin.replace("\r", "").trim
    val resultText = """
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
                     """.stripMargin.replace("\r", "").trim
    doTest(text, resultText)
  }

  def testReplaceWhile3() {
    val text  = """
                  |class X {
                  |  val flag: Boolean
                  |
                  |  def f {
                  |    while<caret>(flag) print("")
                  |  }
                  |}
                """.stripMargin.replace("\r", "").trim
    val resultText = """
                       |class X {
                       |  val flag: Boolean
                       |
                       |  def f {
                       |    if (flag) {
                       |      do print("") while (flag)
                       |    }
                       |  }
                       |}
                     """.stripMargin.replace("\r", "").trim
    doTest(text, resultText)
  }
}
