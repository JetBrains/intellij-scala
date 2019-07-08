package org.jetbrains.plugins.scala
package codeInsight
package intentions
package expression

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 4/29/13
 */
class RemoveUnnecessaryParenthesesIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = InspectionBundle.message("remove.unnecessary.parentheses.fix", "")

  def test_1(): Unit = doTest(
    s"(${caretTag}1 + 1)",
    "1 + 1"
  )

  def test_2(): Unit = doTest(
    s"1 + (1 * 2$caretTag)",
    "1 + 1 * 2"
  )

  def test_3(): Unit = doTest(
    s"""
       |def f(n: Int): Int = n match {
       |  case even if (${caretTag}even % 2 == 0) => (even + 1)
       |  case odd =>  1 + (odd * 3)
       |}""",
    """
      |def f(n: Int): Int = n match {
      |  case even if even % 2 == 0 => (even + 1)
      |  case odd => 1 + (odd * 3)
      |}"""
  )

  def test_4(): Unit = doTest(
    text =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => (even + 1$caretTag)
         |  case odd => 1 + (odd * 3)
         |}""",
    resultText =
      """
        |def f(n: Int): Int = n match {
        |  case even if (even % 2 == 0) => even + 1
        |  case odd => 1 + (odd * 3)
        |}"""
  )

  def test_5(): Unit = doTest(
    text =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => (even + 1)
         |  case odd =>  1 + (odd * 3${caretTag})
         |}""",
    resultText =
      """
        |def f(n: Int): Int = n match {
        |  case even if (even % 2 == 0) => (even + 1)
        |  case odd => 1 + odd * 3
        |}"""
  )

  def test_6(): Unit = doTest(
    s"val a = (($caretTag(1)))",
    "val a = 1"
  )

  def test_7(): Unit = checkIntentionIsNotAvailable(
    s"""1 match {
       |    case i if (${caretTag}i match {case 1 => true}) =>
       |  }"""
  )
}
