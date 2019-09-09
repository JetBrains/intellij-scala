package org.jetbrains.plugins.scala
package codeInsight
package editorActions

import com.intellij.testFramework.EditorTestUtil.CARET_TAG

class ScalaQuoteHandlerTest extends base.ScalaLightCodeInsightFixtureTestAdapter {

  def testScalaFile(): Unit = doTest(
    s"""class Foo {
       |  val foo = $CARET_TAG
       |}""".stripMargin,
    "Foo.scala"
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

  private def doTest(fileText: String, fileName: String)
                    (expected: String): Unit = {
    myFixture.configureByText(fileName, fileText)
    myFixture.`type`('\"')
    myFixture.checkResult(expected, false)
  }
}
