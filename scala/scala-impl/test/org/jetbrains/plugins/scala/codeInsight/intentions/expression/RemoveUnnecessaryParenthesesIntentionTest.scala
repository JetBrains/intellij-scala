package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class RemoveUnnecessaryParenthesesIntentionTest extends ScalaIntentionTestBase {
  override def familyName: String = ScalaBundle.message("remove.unnecessary.parentheses")

  def test_1(): Unit = {
    val text = s"(${CARET}1 + 1)"
    val result = "1 + 1"
    doTest(text, result)
  }

  def test_2(): Unit = {
    val text = s"1 + (1 * 2$CARET)"
    val result = "1 + 1 * 2"
    doTest(text, result)
  }

  def test_3(): Unit = {
    val text =
      s"""def f(n: Int): Int = n match {
         |  case even if (${CARET}even % 2 == 0) => (even + 1)
         |  case odd =>  1 + (odd * 3)
         |}""".stripMargin
    val result =
      """def f(n: Int): Int = n match {
        |  case even if even % 2 == 0 => (even + 1)
        |  case odd => 1 + (odd * 3)
        |}""".stripMargin
    doTest(text, result)
  }

  def test_4(): Unit = {
    val text =
      s"""def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => (even + 1$CARET)
         |  case odd => 1 + (odd * 3)
         |}""".stripMargin
    val result =
      """def f(n: Int): Int = n match {
        |  case even if (even % 2 == 0) => even + 1
        |  case odd => 1 + (odd * 3)
        |}""".stripMargin
    doTest(text, result)
  }

  def test_5(): Unit = {
    val text =
      s"""def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => (even + 1)
         |  case odd =>  1 + (odd * 3$CARET)
         |}""".stripMargin
    val result =
      """def f(n: Int): Int = n match {
        |  case even if (even % 2 == 0) => (even + 1)
        |  case odd => 1 + odd * 3
        |}""".stripMargin
    doTest(text, result)
  }

  def test_6(): Unit = {
    val text = s"val a = (($CARET(1)))"
    val result = "val a = 1"
    doTest(text, result)
  }

  def test_7(): Unit = {
    val text =
      s"""1 match {
         |  case i if (${CARET}i match {case 1 => true}) =>
         |}""".stripMargin
    checkIntentionIsNotAvailable(text)
  }
}
