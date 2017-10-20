package org.jetbrains.plugins.scala
package codeInsight.intentions.controlflow

import org.jetbrains.plugins.scala.codeInsight.intention.controlflow.ReplaceDoWhileWithWhileIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * Nikolay.Tropin
 * 4/17/13
 */
class ReplaceDoWhileWithWhileIntentionTest extends ScalaIntentionTestBase {
  def familyName: String = ReplaceDoWhileWithWhileIntention.familyName

  def testReplaceDoWhile() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    <caret>do {
        |       print("")
        |      //comment
        |    } while(flag)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    print("")
        |    //comment
        |    while (flag) {
        |      print("")
        |      //comment
        |    }
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceDoWhile2() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    do<caret> {
        |      print("")
        |      //comment
        |    } while(flag)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    print("")
        |    //comment
        |    while (flag) {
        |      print("")
        |      //comment
        |    }
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceDoWhile3() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    do {
        |      print("")
        |      //comment
        |    } <caret>while(flag)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    print("")
        |    //comment
        |    while (flag) {
        |      print("")
        |      //comment
        |    }
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceDoWhile4() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    do {
        |      print("")
        |      //comment
        |    } while<caret>(flag)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    print("")
        |    //comment
        |    while (flag) {
        |      print("")
        |      //comment
        |    }
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceDoWhile5() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    if (true)
        |      do {
        |        print("")
        |      } while<caret>(flag)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    if (true) {
        |      print("")
        |      while (flag) {
        |        print("")
        |      }
        |    }
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceDoWhile6() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    <caret>do print("")
        |    while(flag)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    print("")
        |    while (flag) print("")
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceDoWhile7() {
    val text =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    <caret>do print("") while(flag)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  val flag: Boolean
        |
        |  def f {
        |    print("")
        |    while (flag) print("")
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceDoWhile8() {
    val text =
      """
        |class X {
        |  1 match {
        |  case 1 =>
        |    <caret>do {
        |      print("")
        |    } while (true)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  1 match {
        |    case 1 =>
        |      print("")
        |      while (true) {
        |        print("")
        |      }
        |  }
        |}
      """
    doTest(text, resultText)
  }

  def testReplaceDoWhile9() {
    val text =
      """
        |do println("")
        |while (true)
      """
    val resultText =
      """
        |println("")
        |while (true) println("")
      """
    doTest(text, resultText)
  }


}
