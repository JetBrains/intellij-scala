package org.jetbrains.plugins.scala
package codeInsight
package daemon

import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import org.junit.Assert.assertFalse

class ScalaAnnotatorHighlighterVisitorTest extends base.ScalaLightCodeInsightFixtureTestAdapter {

  def testScalaFile(): Unit = doTest(
    "class Foo",
    "Foo.scala"
  )

  def testSbtFile(): Unit = doTest(
    fileText =
      s"""name := "test"
         |
         |version := "0.1"
         |
         |scalaVersion := "${debugger.Scala_2_13.minor}"
         |""".stripMargin,
    fileName = "build.sbt"
  )

  def testWorksheetFile(): Unit = doTest(
    "val foo = 42",
    "worksheet.sc"
  )

  /**
   * The appropriate [[LanguageFileTypeBase]] is supposed to be detected automatically by the file extension.
   *
   * @param fileText the text to load into the in-memory editor.
   * @param fileName the name of the file (which is used to determine the file type based on the registered filename patterns).
   */
  private def doTest(fileText: String, fileName: String): Unit = {
    myFixture.configureByText(fileName, convertLineSeparators(fileText))
    assertFalse(myFixture.doHighlighting().isEmpty)
  }
}
