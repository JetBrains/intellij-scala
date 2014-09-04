package org.jetbrains.plugins.scala
package codeInsight.intentions.booleans

import org.jetbrains.plugins.scala.codeInsight.intention.booleans.SimplifyBooleanExprWithLiteralIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * Nikolay.Tropin
 * 4/29/13
 */
class SimplifyBooleanExprWithLiteralTest extends ScalaIntentionTestBase {
  def familyName: String = SimplifyBooleanExprWithLiteralIntention.familyName

  def test_NotTrue() {
    val text = "<caret>!true"
    val result = "false"
    doTest(text, result)
  }

  def test_TrueEqualsA() {
    val text =
      """val a = true
        |<caret>true == a"""
    val result =
      """val a = true
        |a"""
    doTest(text, result)
  }

  def test_TrueAndA() {
    val text =
      """val a = true
        |true <caret>&& a"""
    val result =
      """val a = true
        |a"""
    doTest(text, result)
  }

  def test_AOrFalse() {
    val text = "val a: Boolean = false; a <caret>| false"
    val result = "val a: Boolean = false; a"
    doTest(text, result)
  }

  def test_TwoExpressions() {
    val text =
      """
        |val a = true
        |<caret>true && (a || false)
      """
    val result =
      """val a = true
        |a"""
    doTest(text, result)
  }

  def test_TrueNotEqualsA() {
    val text =
      """val a = true
        |val flag: Boolean = <caret>true != a"""
    val result =
      """val a = true
        |val flag: Boolean = !a"""
    doTest(text, result)
  }

  def test_SimplifyInParentheses() {
    val text =
      """val a = true
        |!(<caret>true != a)"""
    val result =
      """val a = true
        |!(!a)"""
    doTest(text, result)
  }

  def test_TrueAsAny() {
    val text =
      """
        |def trueAsAny: Any = {
        |  true
        |}
        |if (trueAsAny =<caret>= true) {
        |  println("true")
        |} else {
        |  println("false")
        |}
        |
      """

    checkIntentionIsNotAvailable(text)
  }
}