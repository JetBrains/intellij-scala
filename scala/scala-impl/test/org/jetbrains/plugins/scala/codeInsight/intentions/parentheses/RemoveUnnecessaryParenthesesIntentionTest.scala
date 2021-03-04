package org.jetbrains.plugins.scala
package codeInsight.intentions.parentheses

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * Nikolay.Tropin
 * 6/27/13
 *
 * TODO: merge this test class with [[.expression.RemoveUnnecessaryParenthesesIntentionTest]]?
 */

//test only removing clarifying paretheses here
class RemoveUnnecessaryParenthesesIntentionTest extends ScalaIntentionTestBase {
  override def familyName: String = ScalaBundle.message("remove.unnecessary.parentheses")

  def test_1(): Unit = {
    val text = "1 + (1 * 2<caret>)"
    val result = "1 + 1 * 2"
    doTest(text, result)
  }

  def test_2(): Unit = {
    val text = "1 :: (<caret>2 :: Nil)"
    val result = "1 :: 2 :: Nil"
    doTest(text, result)
  }

  def test_3(): Unit = {
    val text = "(- 1<caret>) + 1"
    val result = "-1 + 1"
    doTest(text, result)
  }

  def test_4(): Unit = {
    val text = "(None<caret> filter (_ => true)) headoption"
    val result = "None filter (_ => true) headoption"
    doTest(text, result)
  }
}
