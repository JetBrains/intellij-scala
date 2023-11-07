package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaCodeInsightTestFixture
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestFixture

class MainMethodLiveTemplateTest extends ScalaLiveTemplateTestBase {

  override protected def templateName: String = "main"

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  private lazy val scalaCompletionFixture: ScalaCompletionTestFixture =
    new ScalaCompletionTestFixture(new ScalaCodeInsightTestFixture(myFixture))

  override def setUp(): Unit = {
    super.setUp()
    //initialize lazy val fixtures
    scalaCompletionFixture
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, this.getTestRootDisposable)
  }

  def testAtBlankLine(): Unit = {
    doTest(
      s"""object Example {
         |  $CARET
         |}""".stripMargin,
      s"""object Example {
         |  def main(args: Array[String]): Unit = {
         |    $CARET
         |  }
         |}""".stripMargin,
    )
  }

  def testAtBlankLine_ViaCompletion(): Unit = {
    scalaCompletionFixture.doCompletionTest(
      s"""object Example {
         |  main$CARET
         |}""".stripMargin,
      s"""object Example {
         |  def main(args: Array[String]): Unit = {
         |    $CARET
         |  }
         |}""".stripMargin,
      templateName
    )
  }

  def testNotAvailableAtNonBlankLine(): Unit = {
    assertIsNotApplicable(
      s"""object Example {
         |  ${CARET}println()
         |}
         |""".stripMargin
    )
  }

  def testNotAvailableAtNonBlankLine_EndOfLineComment(): Unit = {
    assertIsNotApplicable(
      s"""object Example {
         |  //${CARET}
         |}
         |""".stripMargin
    )
  }

  def testInIndentationBasedSyntaxBeforeEof(): Unit = {
    doTest(
      s"""object Example:
         |  $CARET""".stripMargin,
      s"""object Example:
         |  def main(args: Array[String]): Unit = {
         |    $CARET
         |  }""".stripMargin,
    )
  }

  def testInIndentationBasedSyntaxBeforeEof_ViaCompletion(): Unit = {
    scalaCompletionFixture.doCompletionTest(
      s"""object Example:
         |  main$CARET""".stripMargin,
      s"""object Example:
         |  def main(args: Array[String]): Unit = {
         |    $CARET
         |  }""".stripMargin,
      templateName
    )
  }
}
