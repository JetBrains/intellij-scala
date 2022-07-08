package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class ConvertUnderscoreToParameterIntentionTest extends ScalaIntentionTestBase{
  override def familyName = ScalaBundle.message("family.name.convert.underscore.section.to.parameter")

  def testIntroduceExplicitParameter(): Unit = {
    val text = "some.map(_<caret> > 5)"
    val resultText = "some.map(value => value > 5)"

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter2(): Unit = {
    val text = "this.myFun(<caret>_ > 6, _ > 9)"
    val resultText = "this.myFun(value => value > 6, _ > 9)"

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter3(): Unit = {
    val text = "some.foreach(println(<caret>_))"
    val resultText = "some.foreach(x => println(x))"

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter4(): Unit = {
    val text =
      """
        |val name: String = "gfgfgfgfg"
        |val nameHasUpperCase = name.exists(<caret>_.isUpper)
        |""".stripMargin
    val resultText =
      """
        |val name: String = "gfgfgfgfg"
        |val nameHasUpperCase = name.exists(c => c.isUpper)
        |""".stripMargin

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter5(): Unit = {
    val text = "val a2: ((Int, Int, Int) => Int) = <caret>_ + _ + _ + 5"
    val resultText = "val a2: ((Int, Int, Int) => Int) = (i, i1, i2) => i + i1 + i2 + 5"

    doTest(text, resultText)
  }

  def testIntroduceExplicitParameter6(): Unit = {
    val text = "val nameHasUpperCase = name.exists(<caret>_ == 'c')"
    val resultText = "val nameHasUpperCase = name.exists(value => value == 'c')"

    doTest(text, resultText)
  }

}