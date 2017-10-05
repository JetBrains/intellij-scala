package org.jetbrains.plugins.scala.codeInsight.intentions.argument

import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.codeInsight.intention.argument.ArgumentToBlockExpressionIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

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

  def testParameterOnNewLine(): Unit = {
    val settings = CodeStyleSettingsManager.getSettings(getProject)
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
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
