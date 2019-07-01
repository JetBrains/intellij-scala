package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaProjectSettings}

// TODO: maybe, taking into account that there is too much escaping, we should move these tests to files?
class MultiLineStringCopyPasteProcessorTest extends CopyPasteTestBase {
  override protected def setUp(): Unit = {
    super.setUp()
    ScalaProjectSettings.getInstance(getProject).setDontShowConversionDialog(true)
    ScalaProjectSettings.getInstance(getProject).setEnableJavaToScalaConversion(false)
    ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE = CodeInsightSettings.NO
  }

  private def doTestMultiline(from: String, to: String, after: String): Unit = {
    // the hack is used to be able to use triple quotes inside multiline strings in tests
    def replaceQuotes(s: String): String = s.replaceAll("'''", "\"\"\"")

    doTest(
      replaceQuotes(from),
      replaceQuotes(to),
      replaceQuotes(after)
    )
  }

  private def doTestMultilineToEmptyFile(from: String, after: String): Unit = {
    val settings = CodeInsightSettings.getInstance
    val oldReformat = settings.REFORMAT_ON_PASTE
    try {
      settings.REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT
      doTestMultiline(from, Caret, after)
    } finally {
      settings.REFORMAT_ON_PASTE = oldReformat
    }
  }

  def testSimple(): Unit = doTestWithStrip(
    s"""val x = ${Start}42$End""",
    s"""val y = 2${Caret}3""",
    s"""val y = 2423"""
  )

  def testFromMultilineStringToEmptyFile(): Unit = {
    val from =
      s"""'''${Start}first line
         |   second line
         |    third line
         |  $End'''.stripMargin
         |""".stripMargin

    val after =
      """first line
        |   second line
        |    third line
        |  """.stripMargin

    doTestMultilineToEmptyFile(from, after)
  }

  def testFromMultilineStringToEmptyFile_1(): Unit = {
    val from =
      s"""'''${Start}first line
         |   second line
         |    third line$End
         |  '''.stripMargin
         |""".stripMargin

    val after =
      """first line
        |   second line
        |    third line""".stripMargin

    doTestMultilineToEmptyFile(from, after)
  }

  def testFromMultilineMarginStringToEmptyFile(): Unit = {
    val from =
      s"""'''${Start}first line
         |  | second line
         |  |  third line
         |  |$End'''.stripMargin
         |""".stripMargin

    val after =
      """first line
        | second line
        |  third line
        |""".stripMargin

    doTestMultilineToEmptyFile(from, after)
  }

  def testFromMultilineMarginStringToEmptyFile_1(): Unit = {
    val from =
      s"""'''${Start}first line
         |  | second line
         |  |  third line$End
         |  |'''.stripMargin
         |""".stripMargin

    val after =
      """first line
        | second line
        |  third line""".stripMargin

    doTestMultilineToEmptyFile(from, after)
  }

  def testFromInterpMultilineMarginStringToEmptyFile(): Unit = {
    val from =
      s"""s'''${Start}first line
         |   | second line
         |   |  third line
         |   |$End'''.stripMargin
         |""".stripMargin

    val after =
      """first line
        | second line
        |  third line
        |""".stripMargin

    doTestMultilineToEmptyFile(from, after)
  }

  def testFromInterpMultilineMarginStringToEmptyFile_1(): Unit = {
    val from =
      s"""s'''${Start}first line
         |   | second line
         |   |  third line$End
         |   |'''.stripMargin
         |""".stripMargin

    val after =
      """first line
        | second line
        |  third line""".stripMargin

    doTestMultilineToEmptyFile(from, after)
  }

  def testFromMultilineMarginStringToMultilineMarginString(): Unit = {
    val from =
      s"""'''${Start}first line
         |  | second line$End
         |  |  third line
         |  |'''.stripMargin
         |""".stripMargin
    val to =
      s"""'''green yellow
         |  | red blue $Caret
         |  |  orange
         |  |'''.stripMargin
         |""".stripMargin
    val after =
      s"""'''green yellow
         |  | red blue first line
         |  | second line
         |  |  orange
         |  |'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromInterpMultilineMarginStringToMultilineMarginString(): Unit = {
    val from =
      s"""s'''${Start}first line
         |   | second line$End
         |   |  third line
         |   |'''.stripMargin
         |""".stripMargin
    val to =
      s"""'''green yellow
         |  | red blue $Caret
         |  |  orange
         |  |'''.stripMargin
         |""".stripMargin
    val after =
      s"""'''green yellow
         |  | red blue first line
         |  | second line
         |  |  orange
         |  |'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineMarginStringToInterpMultilineMarginString(): Unit = {
    val from =
      s"""s'''${Start}first line
         |   | second line$End
         |   |  third line
         |   |'''.stripMargin
         |""".stripMargin
    val to =
      s"""s'''green yellow
         |   | red blue $Caret
         |   |  orange
         |   |'''.stripMargin
         |""".stripMargin
    val after =
      s"""s'''green yellow
         |   | red blue first line
         |   | second line
         |   |  orange
         |   |'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromInterpMultilineMarginStringToInterpMultilineMarginString(): Unit = {
    val from =
      s"""s'''${Start}first line
         |   | second line$End
         |   |  third line
         |   |'''.stripMargin
         |""".stripMargin
    val to =
      s"""s'''green yellow
         |   | red blue $Caret
         |   |  orange
         |   |'''.stripMargin
         |""".stripMargin
    val after =
      s"""s'''green yellow
         |   | red blue first line
         |   | second line
         |   |  orange
         |   |'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineMarginStringToMultilineString(): Unit = {
    val from =
      s"""'''${Start}first line
         |  | second line
         |  |  third line$End
         |  |'''.stripMargin
         |""".stripMargin
    val to =
      s"""'''green yellow
         |   red blue $Caret
         |    orange
         |  '''.stripMargin
         |""".stripMargin
    val after =
      s"""'''green yellow
         |   red blue first line
         | second line
         |  third line
         |    orange
         |  '''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineMarginStringToMultilineString_1(): Unit = {
    val from =
      s"""'''${Start}first line
         |  | second line
         |  |  third line
         |  |$End'''.stripMargin
         |""".stripMargin
    val to =
      s"""'''green yellow
         |   red blue $Caret
         |    orange
         |  '''.stripMargin
         |""".stripMargin
    val after =
      s"""'''green yellow
         |   red blue first line
         | second line
         |  third line
         |
         |    orange
         |  '''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromInterpMultilineMarginStringToMultilineString(): Unit = {
    val from =
      s"""s'''${Start}first line
         |   | second line
         |   |  third line$End
         |   |'''.stripMargin
         |""".stripMargin
    val to =
      s"""'''green yellow
         |   red blue $Caret
         |    orange
         |  '''.stripMargin
         |""".stripMargin
    val after =
      s"""'''green yellow
         |   red blue first line
         | second line
         |  third line
         |    orange
         |  '''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineStringToMultilineMarginString(): Unit = {
    val from =
      s"""s'''${Start}first line
         |    second line
         |     third line$End
         |   '''.stripMargin
         |""".stripMargin
    val to =
      s"""'''green yellow
         |  | red blue $Caret
         |  |  orange
         |  |'''.stripMargin
         |""".stripMargin
    val after =
      s"""'''green yellow
         |  | red blue first line
         |  |    second line
         |  |     third line
         |  |  orange
         |  |'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineStringToMultilineMarginString_1(): Unit = {
    val from =
      s"""s'''${Start}first line
         |    second line
         |     third line$End
         |   '''.stripMargin
         |""".stripMargin
    val to =
      s"""'''green yellow
         |  | red blue
         |  |  orange
         |  | $Caret'''.stripMargin
         |""".stripMargin
    val after =
      s"""'''green yellow
         |  | red blue
         |  |  orange
         |  | first line
         |  |    second line
         |  |     third line'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineStringToMultilineMarginString_WithoutSomeMarginButWithStripMargin(): Unit = {
    val from =
      s"""s'''${Start}first line
         |    second line
         |     third line$End
         |   '''.stripMargin
         |""".stripMargin
    val to =
      s"""class A {
         |  '''green yellow
         |    | red blue
         |      orange
         |     $Caret'''.stripMargin
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  '''green yellow
         |    | red blue
         |      orange
         |    |first line
         |    |    second line
         |    |     third line'''.stripMargin
         |}
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineStringToMultilineMarginString_WithoutStripMarginButWithAllLinesWithMargin(): Unit = {
    val from =
      s"""s'''${Start}first line
         |    second line
         |     third line$End
         |   '''.stripMargin
         |""".stripMargin
    val to =
      s"""class A {
         |  '''green yellow
         |    | red blue
         |    |  orange
         |     $Caret'''
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  '''green yellow
         |    | red blue
         |    |  orange
         |    |first line
         |    |    second line
         |    |     third line'''
         |}
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineStringToInterpMultilineMarginString(): Unit = {
    val from =
      s"""s'''${Start}first line
         |    second line
         |     third line$End
         |   '''.stripMargin
         |""".stripMargin
    val to =
      s"""s'''green yellow
         |   | red blue
         |   |  orange
         |   | $Caret'''.stripMargin
         |""".stripMargin
    val after =
      s"""s'''green yellow
         |   | red blue
         |   |  orange
         |   | first line
         |   |    second line
         |   |     third line'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testFromMultilineStringToMultilineString(): Unit = {
    val from =
      s"""object Main extends App {
         |  val js =
         |    s'''
         |     function myFunc() {
         |$Start       debugger;
         |$End     }
         |    '''
         |}""".stripMargin
    val to =
      s"""object Main extends App {
         |  val js =
         |    s'''
         |     function myFunc() {
         |       debugger;
         |$Caret
         |     }
         |    '''
         |}""".stripMargin
    val after =
      s"""object Main extends App {
         |  val js =
         |    s'''
         |     function myFunc() {
         |       debugger;
         |       debugger;
         |
         |     }
         |    '''
         |}""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testDoNotHandleIfEditorHasSomeSelection(): Unit ={
    val from =
      s"""s'''${Start}first line
         |    second line$End
         |   '''.stripMargin
         |""".stripMargin
    val to =
      s"""s'''${Start}green yellow$End
         |   | $Caret'''.stripMargin
         |""".stripMargin
    val after =
      s"""s'''first line
         |    second line$Caret
         |   | '''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testEscapeTripleQuotesWhenPastingToMultilineStringAnotherStringWithTripleQuotes(): Unit = {
    val from =
      s"""s$Start'''first line
         |   |  second line
         |   |'''.stripMargin$End
         |""".stripMargin
    val to =
      s"""s'''green yellow
         |   | $Caret'''.stripMargin
         |""".stripMargin
    val after =
      s"""s'''green yellow
         |   | \\\"\\\"\\\"first line
         |   |   |  second line
         |   |   |\\\"\\\"\\\".stripMargin$Caret'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testEscapeTripleQuotesWhenPastingToMultilineStringAnotherStringWithTripleQuotes_1(): Unit = {
    val from =
      s"""s$Start'''first line
         |   |  second line
         |   |'''.stripMargin$End
         |""".stripMargin
    val to =
      s"""s'''green yellow
         |    $Caret'''
         |""".stripMargin
    val after =
      s"""s'''green yellow
         |    \\\"\\\"\\\"first line
         |   |  second line
         |   |\\\"\\\"\\\".stripMargin$Caret'''
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToEmptyMultilineString(): Unit = {
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""'''$Caret
         |'''
         |""".stripMargin
    val after =
      s"""'''first line
         |    second line$Caret
         |'''
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToEmptyMultilineStringWithMargin(): Unit = {
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""'''$Caret
         |'''.stripMargin
         |""".stripMargin
    val after =
      s"""'''first line
         |  |    second line$Caret
         |'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToEmptyInterpMultilineString(): Unit = {
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""s'''$Caret
         | '''
         |""".stripMargin
    val after =
      s"""s'''first line
         |    second line$Caret
         | '''
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToEmptyInterpMultilineStringWithMargin(): Unit = {
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""s'''$Caret
         | '''.stripMargin
         |""".stripMargin
    val after =
      s"""s'''first line
         |   |    second line$Caret
         | '''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }
}
