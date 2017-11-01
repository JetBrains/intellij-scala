package org.jetbrains.plugins.scala.codeInsight
package intentions
package booleans

import org.jetbrains.plugins.scala.codeInsight.intention.booleans.ExpandBooleanIntention

/**
 * @author Ksenia.Sautina
 * @since 6/29/12
 */

class ExpandBooleanIntentionTest extends ScalaIntentionTestBase {
  val familyName = ExpandBooleanIntention.FamilyName

  def testExpandBoolean() {
    val text =
      """
        |class X {
        |  def f(a: Int): Boolean = {
        |    retur<caret>n a > 0
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Int): Boolean = {
        |    <caret>if (a > 0) {
        |      return true
        |    } else {
        |      return false
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testExpandBoolean2() {
    val text =
      """
        |class X {
        |  def f(a: Int): Boolean = {
        |    retur<caret>n (a > 0)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Int): Boolean = {
        |    <caret>if (a > 0) {
        |      return true
        |    } else {
        |      return false
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testExpandBoolean3() {
    val text =
      """
        |class X {
        |  def f(a: Int, b: Int): Boolean = {
        |    retur<caret>n (a > 0 || b < 7)
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Int, b: Int): Boolean = {
        |    <caret>if (a > 0 || b < 7) {
        |      return true
        |    } else {
        |      return false
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testExpandBoolean4() {
    val text =
      """
        |class X {
        |  def f(a: Int, b: Int): Boolean = {
        |    if (a > 0 || b < 7) {
        |      ret<caret>urn true
        |    } else {
        |      return false
        |    }
        |  }
        |}
      """
    val resultText =
      """
        |class X {
        |  def f(a: Int, b: Int): Boolean = {
        |    if (a > 0 || b < 7) {
        |      <caret>if (true) {
        |        return true
        |      } else {
        |        return false
        |      }
        |    } else {
        |      return false
        |    }
        |  }
        |}
      """

    doTest(text, resultText)
  }

}