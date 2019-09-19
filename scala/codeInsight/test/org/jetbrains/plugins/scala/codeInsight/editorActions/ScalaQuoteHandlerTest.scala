package org.jetbrains.plugins.scala
package codeInsight
package editorActions

import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.testFramework.EditorTestUtil.CARET_TAG

class ScalaQuoteHandlerTest extends base.ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaQuoteHandlerTest.QuoteCharacter

  def testScalaFile(): Unit = doTest(
    s"""class Foo {
       |  val foo = $CARET_TAG
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = "$CARET_TAG"
       |}""".stripMargin
  )

  def testSbtFile(): Unit = doTest(
    s"name := $CARET_TAG",
    "build.sbt"
  )(
    s"""name := "$CARET_TAG""""
  )

  def testWorksheetFile(): Unit = doTest(
    s"val foo = $CARET_TAG",
    "worksheet.sc"
  )(
    s"""val foo = "$CARET_TAG""""
  )

  def testMultilineString(): Unit = doTest(
    s"""class Foo {
       |  val foo = ""$CARET_TAG
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = ""${QuoteCharacter + CARET_TAG + QuoteCharacter}""
       |}""".stripMargin,
  )

  def testInterpolatedString(): Unit = doTest(
    s"""class Foo {
       |  val foo = s$CARET_TAG
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = s"$CARET_TAG"
       |}""".stripMargin,
  )

  def testInterpolatedMultilineString(): Unit = doTest(
    s"""class Foo {
       |  val foo = s""$CARET_TAG
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = s""${QuoteCharacter + CARET_TAG + QuoteCharacter}""
       |}""".stripMargin,
  )

  private def doTest(fileText: String, fileName: String = "Foo.scala")
                    (expected: String): Unit = {
    myFixture.configureByText(fileName, convertLineSeparators(fileText))
    myFixture.`type`(QuoteCharacter)
    myFixture.checkResult(convertLineSeparators(expected), false)
  }
}

object ScalaQuoteHandlerTest {
  private val QuoteCharacter = '\"'
}