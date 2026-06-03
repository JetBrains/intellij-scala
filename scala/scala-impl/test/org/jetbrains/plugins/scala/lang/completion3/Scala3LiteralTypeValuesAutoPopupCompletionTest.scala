package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.testFramework.{TestModeFlags, UsefulTestCase}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaCompletionAutoPopupTestCase
import org.junit.Assert.{assertNotNull, assertNull}

import scala.jdk.CollectionConverters._

final class Scala3LiteralTypeValuesAutoPopupCompletionTest extends ScalaCompletionAutoPopupTestCase {
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def setUp(): Unit = {
    super.setUp()
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
  }

  private def doTest(textToType: String, expectedLookupItems: String*)(src: String): Unit = {
    configureByText(src)
    doType(textToType)

    val actualLookupItems = myFixture.getLookupElementStrings
    assertNotNull("myFixture.getLookupElementStrings", actualLookupItems)

    UsefulTestCase.assertContainsElements[String](actualLookupItems, expectedLookupItems.asJava)
  }

  private def doTestNoAutoCompletion(textToType: String, unexpectedLookupItems: String*)(src: String): Unit = {
    configureByText(src)
    doType(textToType)

    assertNull("Lookup shouldn't be shown", getLookup)
    if (getLookup != null) {
      val actualLookupItems = myFixture.getLookupElementStrings
      if (actualLookupItems != null) {
        UsefulTestCase.assertDoesntContain[String](actualLookupItems, unexpectedLookupItems.asJava)
      }
    }
  }

  def testUnionTypeOpeningQuotes(): Unit = doTest("\"", "red", "green", "blue") {
    s"""
       |val color: "red" | "green" | "blue" = $CARET
       |""".stripMargin
  }

  def testUnionTypeAliasOpeningQuotes(): Unit = doTest("\"", "red", "green", "blue") {
    s"""
       |type Color = "red" | "green" | "blue"
       |val color: Color = $CARET
       |""".stripMargin
  }

  def testUnionTypeAliasInsideQuotes(): Unit = doTest("r", "red", "green") {
    s"""
       |type Color = "red" | "green" | "blue"
       |val color: Color = "$CARET"
       |""".stripMargin
  }

  def testUnionTypeInsideQuotesAfterSpace(): Unit = doTest("e", "red", "green", "blue") {
    s"""
       |val color: "red" | "green" | "blue" = " $CARET"
       |""".stripMargin
  }

  def testNoCompletionUnionTypeInsideQuotesAfterSomeText(): Unit = doTestNoAutoCompletion("r", "red", "green", "blue") {
    s"""
       |val color: "red" | "green" | "blue" = "gibberish$CARET"
       |""".stripMargin
  }

  def testNoCompletionUnionTypeInsideQuotesAfterSomeTextAndSpace(): Unit = doTestNoAutoCompletion("e", "red", "green", "blue") {
    s"""
       |val color: "red" | "green" | "blue" = "gibberish $CARET"
       |""".stripMargin
  }
}
