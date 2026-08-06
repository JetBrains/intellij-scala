package org.jetbrains.plugins.scala
package codeInsight
package editorActions

import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => Caret}
import org.jetbrains.plugins.scala.base.EditorActionTestBase

class ScalaQuoteHandlerTest extends EditorActionTestBase {

  import ScalaQuoteHandlerTest._

  def testScalaFile(): Unit = doTest(
    s"""class Foo {
       |  val foo = $Caret
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = "$Caret"
       |}""".stripMargin
  )

  def testSbtFile(): Unit = doTest(
    s"name := $Caret",
    "build.sbt"
  )(
    s"""name := "$Caret""""
  )

  def testWorksheetFile(): Unit = doTest(
    s"val foo = $Caret",
    "worksheet.sc"
  )(
    s"""val foo = "$Caret""""
  )

  def testMultilineString(): Unit = doTest(
    s"""class Foo {
       |  val foo = ""$Caret
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = "$QuotedCaret"
       |}""".stripMargin
  )

  def testInterpolatedString(): Unit = doTest(
    s"""class Foo {
       |  val foo = s$Caret
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = s"$Caret"
       |}""".stripMargin
  )

  def testInterpolatedMultilineString(): Unit = doTest(
    s"""class Foo {
       |  val foo = s""$Caret
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = s"$QuotedCaret"
       |}""".stripMargin
  )

  def testQuotedIdentifierBegin(): Unit = doTest(
    s"""class Foo {
       |  val $Caret
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val `$Caret`
       |}""".stripMargin,
    PatternCharacter
  )

  def testQuotedIdentifierEnd(): Unit = doTest(
    s"""class Foo {
       |  val `$Caret
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val ``$Caret
       |}""".stripMargin,
    PatternCharacter
  )

  def testPatternBegin(): Unit = doTest(
    s"""class Foo {
       |  ??? match {
       |    case $Caret
       |  }
       |}""".stripMargin
  )(
    s"""class Foo {
       |  ??? match {
       |    case `$Caret`
       |  }
       |}""".stripMargin,
    PatternCharacter
  )

  def testPatternEnd(): Unit = doTest(
    s"""class Foo {
       |  ??? match {
       |    case `$Caret
       |  }
       |}""".stripMargin
  )(
    s"""class Foo {
       |  ??? match {
       |    case ``$Caret
       |  }
       |}""".stripMargin,
    PatternCharacter
  )

  def testCharBegin(): Unit = doTest(
    s"""class Foo {
       |  val foo = $Caret
       |}""".stripMargin
  )(
    s"""class Foo {
       |  val foo = '$Caret'
       |}""".stripMargin,
    CharCharacter
  )

    def testCharEnd(): Unit = doTest(
      s"""class Foo {
         |  val foo = '$Caret
         |}""".stripMargin
    )(
      s"""class Foo {
         |  val foo = ''$CARET
         |}""".stripMargin,
      CharCharacter
    )

  private def doTest(fileText: String,
                     fileName: String = "Foo.scala")
                    (expected: String,
                     character: Char = QuoteCharacter): Unit = {
    myFixture.configureByText(fileName, convertLineSeparators(fileText))
    myFixture.`type`(character)
    myFixture.checkResult(convertLineSeparators(expected), false)
  }
}

object ScalaQuoteHandlerTest {
  private val QuoteCharacter = '\"'
  private val PatternCharacter = '`'
  private val CharCharacter = '\''
  private val QuotedCaret = QuoteCharacter.toString + (QuoteCharacter.toString + Caret + QuoteCharacter.toString) + QuoteCharacter.toString
}