package org.jetbrains.plugins.scala.codeInsight.intention.argument

import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, intentions}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.runner.RunWith

import scala.annotation.nowarn

abstract class ArgumentToBlockExpressionIntentionTestBase extends intentions.ScalaIntentionTestBase {
  override def familyName = ScalaCodeInsightBundle.message("family.name.convert.to.block.expression")

  protected def setClosureParametersOnNewLineSetting(): Unit = {
    val settings = CodeStyleSettingsManager.getSettings(getProject): @nowarn("cat=deprecation")
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.PLACE_CLOSURE_PARAMETERS_ON_NEW_LINE = true
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
final class ArgumentToBlockExpressionIntentionTest extends ArgumentToBlockExpressionIntentionTestBase {

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
    setClosureParametersOnNewLineSetting()

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

final class ArgumentToBlockExpressionIntentionTest_Scala3 extends ArgumentToBlockExpressionIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testMultiline(): Unit = {
    val text =
      s"""
         |class Test {
         |  method(x, y)$CARET(x =>
         |    x)
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

  def testMultiline2(): Unit = {
    val text =
      s"""
         |class Test {
         |  method(x, y)$CARET(
         |    x =>
         |      x
         |  )
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

  def testFewerBraces(): Unit = {
    val text =
      s"""
         |class Test {
         |  method(x, y)$CARET: x =>
         |    x
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

  def testFewerBraces2(): Unit = {
    val text =
      s"""
         |class Test {
         |  method(x, y)$CARET:
         |    x => x
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

  def testFewerBraces3(): Unit = {
    val text =
      s"""
         |class Test {
         |  method(x, y)$CARET:
         |    x =>
         |      x
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

  def testFewerBracesParameterOnNewLine(): Unit = {
    setClosureParametersOnNewLineSetting()

    val text =
      s"""
         |class Test {
         |  method(x, y)$CARET: x =>
         |    x
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

  def testFewerBracesCursorAfterColon(): Unit = doTest(
    s"""
       |call:$CARET
       |  ???
       |""".stripMargin,
    """
      |call {
      |  ???
      |}
      |""".stripMargin
  )
}
