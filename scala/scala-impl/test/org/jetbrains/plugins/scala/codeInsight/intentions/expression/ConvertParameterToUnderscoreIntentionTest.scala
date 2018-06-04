package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intention.expression.ConvertParameterToUnderscoreIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * @author Ksenia.Sautina
 * @since 4/18/12
 */

class ConvertParameterToUnderscoreIntentionTest  extends ScalaIntentionTestBase{
  def familyName = ConvertParameterToUnderscoreIntention.familyName

  def testIntroduceImplicitParameter() {
    val text = "some.map(<caret>x => x > 5)"
    val resultText= "some.map(<caret>_ > 5)"

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter2() {
    val text = "this.myFun(<caret>x1 => x1 > 6, x2 => x2 > 9)"
    val resultText= "this.myFun(<caret>_ > 6, x2 => x2 > 9)"

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter3() {
    val text = "some.foreach(<caret>x => println(x))"
    val resultText= "some.foreach(<caret>println(_))"

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter4() {
    val text = "val nameHasUpperCase = name.exists(<caret>x => x.isUpper)"
    val resultText= "val nameHasUpperCase = name.exists(<caret>_.isUpper)"

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter5() {
    val text = "this.myFun2(<caret>x1 => x1.isEmpty, _.isEmpty)"
    val resultText= "this.myFun2(<caret>_.isEmpty, _.isEmpty)"

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter6() {
    val text = "val a: ((Int, Int, Int) => Int) = (i, i1, i2) <caret>=> i + i1 + i2 + 5"
    val resultText= "val a: ((Int, Int, Int) => Int) = <caret>_ + _ + _ + 5"

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter7() {
    val text = """
    | val x: Int => Int = i<caret> => {
    |   i + {
    |     1 + {
    |       i
    |     }
    |   }
    | }
    """
    val resultText= """
    | val x: Int => Int = i => {
    |   i + {
    |     1 + {
    |       i
    |     }
    |   }
    | }
    """

    try {
      doTest(text, resultText)
    } catch {
      case _: RuntimeException => // Expected, so continue
    }
  }

  def testIntroduceImplicitParameter8() {
    val text = "val a: (Int => Int) = i =<caret>> i + i + i + 5"
    val resultText= "val a: (Int => Int) = i => i + i + i + 5"

    try {
      doTest(text, resultText)
    } catch {
      case _: RuntimeException => // Expected, so continue
    }
  }

  def testIntroduceImplicitParameter9() {
    val text = "val a: ((Int, Int, Int) => Int) = (i, i1, i2) =><caret> i + i2 + i1 + 5"
    val resultText= "val a: ((Int, Int, Int) => Int) = (i, i1, i2) => i + i2 + i1 + 5"

    try {
      doTest(text, resultText)
    } catch {
      case _: RuntimeException => // Expected, so continue
    }
  }

  def testIntroduceImplicitParameter10() {
    val text = "val a: ((Int, Int, Int) => Int) = (i, i1) =><caret> i + i1 + 5"
    val resultText = "val a: ((Int, Int, Int) => Int) = _ + _ + 5"

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter11() {
    val text = """
    |val x: Int => Int = i<caret> => {
    |  i + {
    |    1
    |  }
    |}
    """
    val resultText = """
    |val x: Int => Int = <caret>{
    |  _ + {
    |    1
    |  }
    |}
    """

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter12() {
    val text = "(x: Int)<caret> => x + 1"
    val resultText = "<caret>(_: Int) + 1"

    doTest(text, resultText)
  }

  def testIntroduceImplicitParameter13() {
    val text = "(x: Double, y: Dou<caret>ble) => x + 6 + y"
    val resultText = "<caret>(_: Double) + 6 + (_: Double)"

    doTest(text, resultText)
  }


}
