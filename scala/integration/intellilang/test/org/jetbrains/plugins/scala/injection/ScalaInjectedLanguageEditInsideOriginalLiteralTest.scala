package org.jetbrains.plugins.scala.injection

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * Tests for editing code inside a string literal with an injected file
 */
class ScalaInjectedLanguageEditInsideOriginalLiteralTest extends EditorActionTestBase {

  import EditorTestUtil.{CARET_TAG => Caret}
  import org.jetbrains.plugins.scala.util.MultilineStringUtil.{MultilineQuotes => Quotes}

  private var scalaInjectionTestFixture: ScalaInjectionTestFixture = _

  override protected def setUp(): Unit = {
    super.setUp()

    scalaInjectionTestFixture = new ScalaInjectionTestFixture(getProject, getFixture)
  }

  private def doEnterTestInInjection(before: String, after: String): Unit = {
    performTest(before, after) { () =>
      scalaInjectionTestFixture.assertHasSomeInjectedLanguageAtCaret()
      performEnterAction()
    }
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineString(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    |  "a" : 42,$Caret
         |    |  "b" : 23
         |    |}$Quotes.stripMargin
         |""".stripMargin

    val after =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    |  "a" : 42,
         |    |  $Caret
         |    |  "b" : 23
         |    |}$Quotes.stripMargin
         |""".stripMargin

    doEnterTestInInjection(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineStringWithNonDefaultMargin(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    #  "a" : 42,$Caret
         |    #  "b" : 23
         |    #}$Quotes.stripMargin('#')
         |""".stripMargin

    val after =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    #  "a" : 42,
         |    #  $Caret
         |    #  "b" : 23
         |    #}$Quotes.stripMargin('#')
         |""".stripMargin

    doEnterTestInInjection(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineInterpolatedString(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     |  "a" : 42,$Caret
         |     |  "b" : 23
         |     |}$Quotes.stripMargin
         |""".stripMargin

    val after =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     |  "a" : 42,
         |     |  $Caret
         |     |  "b" : 23
         |     |}$Quotes.stripMargin
         |""".stripMargin

    doEnterTestInInjection(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineInterpolatedStringWithNonDefaultMargin(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     #  "a" : 42,$Caret
         |     #  "b" : 23
         |     #}$Quotes.stripMargin('#')
         |""".stripMargin

    val after =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     #  "a" : 42,
         |     #  $Caret
         |     #  "b" : 23
         |     #}$Quotes.stripMargin('#')
         |""".stripMargin

    doEnterTestInInjection(before, after)
  }
}
