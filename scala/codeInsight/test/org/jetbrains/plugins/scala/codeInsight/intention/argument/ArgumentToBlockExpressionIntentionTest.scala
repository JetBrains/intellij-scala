package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

import scala.annotation.nowarn

class ArgumentToBlockExpressionIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.convert.to.block.expression")

  def test(): Unit = {
    val text =
      s"""
         |class Test {
         |  method(x, y)$CARET( x => x)
         |}
      """.stripMargin

    val resultText =
      s"""
         |class Test {
         |  method(x, y)$CARET { x =>
         |    x
         |  }
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testParameterOnNewLine(): Unit = {
    val settings = CodeStyleSettingsManager.getSettings(getProject): @nowarn("cat=deprecation")
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = true

    val text =
      s"""
         |class Test {
         |  method(x, y)$CARET( x => x)
         |}
      """.stripMargin

    val resultText =
      s"""
         |class Test {
         |  method(x, y)$CARET {
         |    x =>
         |      x
         |  }
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def testCursorAfterParenthesis(): Unit = doTest(
    s"""
      |call($CARET???)
      |""".stripMargin,
    """
      |call {
      |  ???
      |}
      |""".stripMargin
  )

  def testCursorAfterParenthesis2(): Unit = doTest(
    s"""
       |call(???)$CARET
       |after
       |""".stripMargin,
    """
      |call {
      |  ???
      |}
      |after
      |""".stripMargin
  )
}
