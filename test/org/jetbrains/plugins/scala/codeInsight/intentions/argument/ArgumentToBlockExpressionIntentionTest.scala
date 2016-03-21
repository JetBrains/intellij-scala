package org.jetbrains.plugins.scala.codeInsight.intentions.argument

import org.jetbrains.plugins.scala.codeInsight.intention.argument.ArgumentToBlockExpressionIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
  * @author Roman.Shein
  * @since 21.03.2016.
  */
class ArgumentToBlockExpressionIntentionTest extends ScalaIntentionTestBase {
  override def familyName = ArgumentToBlockExpressionIntention.familyName

  def test() {
    val text =
      """
        |class Test {
        |  method(x, y)<caret>( x => x)
        |}
      """

    val resultText =
      """
        |class Test {
        |  method(x, y)<caret> { x =>
        |    x
        |  }
        |}
      """

    doTest(text, resultText)
  }

  def testParameterdOnNewLine(): Unit = {
    val scalaSettings = getScalaCodeStyleSettings
    scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = true

    val text =
      """
        |class Test {
        |  method(x, y)<caret>( x => x)
        |}
      """

    val resultText =
      """
        |class Test {
        |  method(x, y)<caret> {
        |    x =>
        |      x
        |  }
        |}
      """

    doTest(text, resultText)
  }

}
