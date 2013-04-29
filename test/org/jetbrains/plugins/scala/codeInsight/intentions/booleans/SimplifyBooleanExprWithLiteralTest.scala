package org.jetbrains.plugins.scala
package codeInsight.intentions.booleans

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.codeInsight.intention.booleans.SimplifyBooleanExprWithLiteralIntention

/**
 * Nikolay.Tropin
 * 4/29/13
 */
class SimplifyBooleanExprWithLiteralTest extends ScalaIntentionTestBase{
  def familyName: String = SimplifyBooleanExprWithLiteralIntention.familyName

  def test_NotTrue() {
    val text = "<caret>!true"
    val result = "false"
    doTest(text, result)
  }

  def test_TrueEqualsA() {
    val text = "<caret>true == a"
    val result = "a"
    doTest(text, result)
  }

  def test_TrueAndA() {
    val text = "(<caret>true && a)"
    val result = "a"
    doTest(text, result)
  }

  def test_AOrFalse() {
    val text = "val a: Boolean = false; a <caret>| false"
    val result = "val a: Boolean = false; a"
    doTest(text, result)
  }

  def test_TwoExpressions() {
    val text = "<caret>true && (a || false)"
    val result = "a"
    doTest(text, result)
  }

  def test_TrueNotEqualsA() {
    val text = "val flag: Boolean = <caret>true != a"
    val result = "val flag: Boolean = !a"
    doTest(text, result)
  }

  def test_SimplifyInParentheses() {
    val text = "!(<caret>true != a)"
    val result = "!(!a)"
    doTest(text, result)
  }

}
