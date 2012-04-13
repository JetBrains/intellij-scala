package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import codeInsight.intention.expression.ConvertToInfixMethodCallIntention
import codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/9/12
 */

class ConvertToInfixMethodCallTest extends ScalaIntentionTestBase {
  val familyName = ConvertToInfixMethodCallIntention.familyName

  def testConvertToInfixMethodCall() {
    val text = "1.<caret>to(5)"
    val resultText = "1 <caret>to 5"

    doTest(text, resultText)
  }

  def testConvertToInfixMethodCall1() {
    val text = "1.<caret>to(5, 7)"
    val resultText = "1 <caret>to (5, 7)"

    doTest(text, resultText)
  }

  def testConvertToInfixMethodCall2() {
    val text = "new A().f<caret>oo(2)"
    val resultText = "new A() f<caret>oo 2"

    doTest(text, resultText)
  }

  def testConvertToInfixMethodCall3() {
    val text = "(new A).foo<caret>(2)"
    val resultText = "new A foo<caret> 2"

    doTest(text, resultText)
  }

  def testConvertToInfixMethodCall4() {
    val text = "1 :: Nil.:<caret>:(2)"
    val resultText = "1 :: 2 :<caret>: Nil"

    doTest(text, resultText)
  }

  def testConvertToInfixMethodCall5() {
    val text = "x.<caret>foo(g2)"
    val resultText = "x <caret>foo g2"

    doTest(text, resultText)
  }

  def testConvertToInfixMethodCall6() {
    val text = "x.goo<caret>(1, (1 + 2) * 3)"
    val resultText = "x goo<caret> (1, (1 + 2) * 3)"

    doTest(text, resultText)
  }

  def testConvertToInfixMethodCall7() {
    val text = "(l2 :: Nil).:<caret>:(l1)"
    val resultText = "l1 :<caret>: l2 :: Nil"

    doTest(text, resultText)
  }

  def testConvertToInfixMethodCall8() {
    val text = "1 + 2 :: Nil.::<caret>(3 + 4)"
    val resultText = "1 + 2 :: 3 + 4 ::<caret> Nil"

    doTest(text, resultText)
  }

  def testConvertFromInfixMethodCall9() {
    val text = "1.<caret>+(2 * 3)"
    val resultText = "1 <caret>+ 2 * 3"

    doTest(text, resultText)
  }

  def testConvertFromInfixMethodCall10() {
    val text = "x.map<caret>(_ > 9)"
    val resultText = "x map<caret> (_ > 9)"

    doTest(text, resultText)
  }

}
