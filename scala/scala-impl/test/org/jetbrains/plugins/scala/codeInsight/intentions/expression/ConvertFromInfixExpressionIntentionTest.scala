package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class ConvertFromInfixExpressionIntentionTest extends ScalaIntentionTestBase {
  override val familyName = ScalaBundle.message("family.name.convert.from.infix.expression")

  def testConvertFromInfixExpression(): Unit = {
    val text = "1 <caret>to 5"
    val resultText = "1.<caret>to(5)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression1(): Unit = {
    val text = "1 <caret>to (5, 7)"
    val resultText = "1.<caret>to(5, 7)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression2(): Unit = {
    val text = "new A() f<caret>oo 2"
    val resultText = "new A().f<caret>oo(2)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression3(): Unit = {
    val text = "new A foo<caret> 2"
    val resultText = "(new A).foo<caret>(2)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression4(): Unit = {
    val text = "1 :: 2 :<caret>: Nil"
    val resultText = "1 :: Nil.:<caret>:(2)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression5(): Unit = {
    val text = "x <caret>foo g2"
    val resultText = "x.<caret>foo(g2)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression6(): Unit = {
    val text = "x goo<caret>(1, (1 + 2) * 3)"
    val resultText = "x.goo<caret>(1, (1 + 2) * 3)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression7(): Unit = {
    val text = "l1 :<caret>: l2 :: Nil"
    val resultText = "(l2 :: Nil).:<caret>:(l1)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression8(): Unit = {
    val text = "1 + 2 :: 3 + 4 ::<caret> Nil"
    val resultText = "1 + 2 :: Nil.::<caret>(3 + 4)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression9(): Unit = {
    val text = "1 <caret>+ 2 * 3"
    val resultText = "1.<caret>+(2 * 3)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression10(): Unit = {
    val text = "x map<caret> (_ > 9)"
    val resultText = "x.map<caret>(_ > 9)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression11(): Unit = {
    val text = "this <caret>foo 1"
    val resultText = "this.<caret>foo(1)"

    doTest(text, resultText)
  }

  def testConvertFromInfixExpression12(): Unit = {
    val text =
      """
        |case class M[A](a: A) {
        |  def map[B](f: A => B): M[B] = M(f(a))
        |}
        |
        |M(1) <caret>map[String] (_.toString)
      """.stripMargin
    val resultText =
      """
        |case class M[A](a: A) {
        |  def map[B](f: A => B): M[B] = M(f(a))
        |}
        |
        |M(1).<caret>map[String](_.toString)
      """.stripMargin
    doTest(text, resultText)
  }

}
