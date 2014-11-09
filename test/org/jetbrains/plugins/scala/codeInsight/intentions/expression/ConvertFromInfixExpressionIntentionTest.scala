package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intention.expression.ConvertFromInfixExpressionIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/9/12
 */

class ConvertFromInfixExpressionIntentionTest extends ScalaIntentionTestBase {
  val familyName = ConvertFromInfixExpressionIntention.familyName

  def testConvertFromInfixExpression() {
    val text = "1 <caret>to 5"
    val resultText = "1.<caret>to(5)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression1() {
    val text = "1 <caret>to (5, 7)"
    val resultText = "1.<caret>to(5, 7)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression2() {
    val text = "new A() f<caret>oo 2"
    val resultText = "new A().f<caret>oo(2)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression3() {
    val text = "new A foo<caret> 2"
    val resultText = "(new A).foo<caret>(2)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression4() {
    val text = "1 :: 2 :<caret>: Nil"
    val resultText = "1 :: Nil.:<caret>:(2)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression5() {
    val text = "x <caret>foo g2"
    val resultText = "x.<caret>foo(g2)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression6() {
    val text = "x goo<caret>(1, (1 + 2) * 3)"
    val resultText = "x.goo<caret>(1, (1 + 2) * 3)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression7() {
    val text = "l1 :<caret>: l2 :: Nil"
    val resultText = "(l2 :: Nil).:<caret>:(l1)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression8() {
    val text = "1 + 2 :: 3 + 4 ::<caret> Nil"
    val resultText = "1 + 2 :: Nil.::<caret>(3 + 4)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression9() {
    val text = "1 <caret>+ 2 * 3"
    val resultText = "1.<caret>+(2 * 3)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression10() {
    val text = "x map<caret> (_ > 9)"
    val resultText = "x.map<caret>(_ > 9)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression11() {
    val text = "this <caret>foo 1"
    val resultText = "this.<caret>foo(1)"

    doTest(text, resultText)
  }

}
