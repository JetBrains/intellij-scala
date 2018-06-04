package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intention.expression.ConvertUnderscoreToParameterIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/13/12
 */

class ConvertUnderscoreToParameterIntentionTest extends ScalaIntentionTestBase{
  def familyName = ConvertUnderscoreToParameterIntention.familyName

  def testIntroduceExplicitParameter() {
    val text = "some.map(_<caret> > 5)"
    val resultText = "some.map(value => value > 5)"

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter2() {
    val text = "this.myFun(<caret>_ > 6, _ > 9)"
    val resultText = "this.myFun(value => value > 6, _ > 9)"

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter3() {
    val text = "some.foreach(println(<caret>_))"
    val resultText = "some.foreach(x => println(x))"

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter4() {
    val text =
      """
        |val name: String = "gfgfgfgfg"
        |val nameHasUpperCase = name.exists(<caret>_.isUpper)
      """
    val resultText =
      """
        |val name: String = "gfgfgfgfg"
        |val nameHasUpperCase = name.exists(c => c.isUpper)
      """

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter5() {
    val text = "val a2: ((Int, Int, Int) => Int) = <caret>_ + _ + _ + 5"
    val resultText = "val a2: ((Int, Int, Int) => Int) = (i, i1, i2) => i + i1 + i2 + 5"

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter6() {
    val text = "val nameHasUpperCase = name.exists(<caret>_ == 'c')"
    val resultText = "val nameHasUpperCase = name.exists(value => value == 'c')"

    doTest(text, resultText)
  }

}