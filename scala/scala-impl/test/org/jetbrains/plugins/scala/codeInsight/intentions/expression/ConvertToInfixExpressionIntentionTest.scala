package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intention.expression.ConvertToInfixExpressionIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/9/12
 */

class ConvertToInfixExpressionIntentionTest extends ScalaIntentionTestBase {
  val familyName = ConvertToInfixExpressionIntention.familyName

  def testConvertToInfixExpression() {
    val text = "1.<caret>to(5)"
    val resultText = "1 <caret>to 5"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression1() {
    val text = "1.<caret>to(5, 7)"
    val resultText = "1 <caret>to(5, 7)"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression2() {
    val text = "new A().f<caret>oo(2)"
    val resultText = "new A() f<caret>oo 2"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression3() {
    val text = "(new A).foo<caret>(2)"
    val resultText = "new A foo<caret> 2"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression4() {
    val text = "1 :: Nil.:<caret>:(2)"
    val resultText = "1 :: 2 :<caret>: Nil"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression5() {
    val text = "x.<caret>foo(g2)"
    val resultText = "x <caret>foo g2"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression6() {
    val text = "x.goo<caret>(1, (1 + 2) * 3)"
    val resultText = "x goo<caret>(1, (1 + 2) * 3)"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression7() {
    val text = "(l2 :: Nil).:<caret>:(l1)"
    val resultText = "l1 :<caret>: l2 :: Nil"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression8() {
    val text = "1 + 2 :: Nil.::<caret>(3 + 4)"
    val resultText = "1 + 2 :: 3 + 4 ::<caret> Nil"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression9() {
    val text = "1.<caret>+(2 * 3)"
    val resultText = "1 <caret>+ 2 * 3"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression10() {
    val text = "x.map<caret>(_ > 9)"
    val resultText = "x map<caret> (_ > 9)"

    doTest(text, resultText)
  }

  def testConvertToInfixExpression11() {
    val text = "this.<caret>foo(1)"
    val resultText = "this <caret>foo 1"

    doTest(text, resultText)
  }

  def testConvertToInfix12() = {
    val text =
      """
        |case class M[A](a: A) {
        |  def map[B](f: A => B): M[B] = M(f(a))
        |}
        |
        |M(1).<caret>map[String](_.toString)
      """.stripMargin
    val resultText =
      """
        |case class M[A](a: A) {
        |  def map[B](f: A => B): M[B] = M(f(a))
        |}
        |
        |M(1) <caret>map[String] (_.toString)
      """.stripMargin

    doTest(text, resultText)
  }

}
