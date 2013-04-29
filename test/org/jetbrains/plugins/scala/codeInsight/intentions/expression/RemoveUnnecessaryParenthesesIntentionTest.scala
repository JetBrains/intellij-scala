package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.codeInsight.intention.expression.RemoveUnnecessaryParenthesesIntention

/**
 * Nikolay.Tropin
 * 4/29/13
 */
class RemoveUnnecessaryParenthesesIntentionTest extends ScalaIntentionTestBase {
  def familyName: String = RemoveUnnecessaryParenthesesIntention.familyName

  def test_1() {
    val text = "(<caret>1 + 1)"
    val result = "1 + 1"
    doTest(text, result)
  }

  def test_2() {
    val text = "1 + (1 * 2<caret>)"
    val result = "1 + 1 * 2"
    doTest(text, result)
  }

  def test_3() {
    val text  = """
                  |def f(n: Int): Int = n match {
                  |  case even if (<caret>even % 2 == 0) => (even + 1)
                  |  case odd =>  1 + (odd * 3)
                  |}
                """.stripMargin.replace("\r", "").trim
    val result = """
                   |def f(n: Int): Int = n match {
                   |  case even if even % 2 == 0 => (even + 1)
                   |  case odd => 1 + (odd * 3)
                   |}
                 """.stripMargin.replace("\r", "").trim
    doTest(text, result)
  }

  def test_4() {
    val text  = """
                  |def f(n: Int): Int = n match {
                  |  case even if (even % 2 == 0) => (even + 1<caret>)
                  |  case odd => 1 + (odd * 3)
                  |}
                """.stripMargin.replace("\r", "").trim
    val result = """
                   |def f(n: Int): Int = n match {
                   |  case even if (even % 2 == 0) => even + 1
                   |  case odd => 1 + (odd * 3)
                   |}
                 """.stripMargin.replace("\r", "").trim
    doTest(text, result)
  }

  def test_5() {
    val text  = """
                  |def f(n: Int): Int = n match {
                  |  case even if (even % 2 == 0) => (even + 1)
                  |  case odd =>  1 + (odd * 3<caret>)
                  |}
                """.stripMargin.replace("\r", "").trim
    val result = """
                   |def f(n: Int): Int = n match {
                   |  case even if (even % 2 == 0) => (even + 1)
                   |  case odd => 1 + odd * 3
                   |}
                 """.stripMargin.replace("\r", "").trim
    doTest(text, result)
  }

  def test_6() {
    val text = "val a = ((<caret>(1)))"
    val result = "val a = 1"
    doTest(text, result)
  }
}
