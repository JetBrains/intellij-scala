package org.jetbrains.plugins.scala.lang.actions.editor.copy

import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaProjectSettings}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

// Q: maybe, taking into account that there is too much escaping, we should move these tests to files? Not sure...
@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
class MultiLineStringCopyPastePreProcessorTest extends CopyPasteTestBase {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectSettings = ScalaProjectSettings.getInstance(getProject)
    projectSettings.setDontShowConversionDialog(true)
    projectSettings.setEnableJavaToScalaConversion(false)

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

  def testPasteToIntLiteral_Start(): Unit = doTest(
    s"""val x = ${Start}42$End""",
    s"""val y = ${Caret}23""",
    s"""val y = 4223"""
  )

  def testPasteToIntLiteral_Middle(): Unit = doTest(
    s"""val x = ${Start}42$End""",
    s"""val y = 2${Caret}3""",
    s"""val y = 2423"""
  )

  def testPasteToIntLiteral_End(): Unit = doTest(
    s"""val x = ${Start}42$End""",
    s"""val y = 23$Caret""",
    s"""val y = 2342"""
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

  def testFromMultilineStringToMultilineMarginString_WithoutStripMarginButWithAllLines_WithMargin(): Unit = {
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

  def testIfEditorHasSomeSelectionInsideLiteralContent(): Unit ={
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
         |   |    second line$Caret
         |   | '''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testDoNotHandleIfEditorHasSomeSelectionOutsideLiteralContent(): Unit ={
    val from =
      s"""s'''${Start}first line
         |    second line$End
         |   '''.stripMargin
         |""".stripMargin
    val to =
      s"""s""$Start"green yellow$End
         |   | $Caret'''.stripMargin
         |""".stripMargin
    // indention is strange due to it actually becomes a broken string (quote is included with selection)
    val after =
      s"""s""first line
         |second line$Caret
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

  //
  // paste to empty multiline string
  //

  private def doTestMultilineForAnyInsertMarginSetting(from: String, to: String, after: String): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = false
    doTestMultiline(from, to, after)
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    doTestMultiline(from, to, after)
  }

  def testToEmptyOneLineMultilineString(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = false
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""'''$Caret'''
         |""".stripMargin
    val after =
      s"""'''first line
         |    second line$Caret'''
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToEmptyOneLineMultilineString_1(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""'''$Caret'''
         |""".stripMargin
    val after =
      s"""'''first line
         |  |    second line$Caret'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToEmptyOneLineMultilineString_WithMargin(): Unit = {
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""'''$Caret'''.stripMargin
         |""".stripMargin
    val after =
      s"""'''first line
         |  |    second line$Caret'''.stripMargin
         |""".stripMargin
    doTestMultilineForAnyInsertMarginSetting(from, to, after)
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
    doTestMultilineForAnyInsertMarginSetting(from, to, after)
  }

  def testToEmptyMultilineString_WithMargin(): Unit = {
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
    doTestMultilineForAnyInsertMarginSetting(from, to, after)
  }

  def testToEmptyInterpOneLineMultilineString(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = false
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""s'''$Caret'''
         |""".stripMargin
    val after =
      s"""s'''first line
         |    second line$Caret'''
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToEmptyInterpOneLineMultilineString_1(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""s'''$Caret'''
         |""".stripMargin
    val after =
      s"""s'''first line
         |   |    second line$Caret'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToEmptyInterpOneLineMultilineString_WithMargin(): Unit = {
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""s'''$Caret'''.stripMargin
         |""".stripMargin
    val after =
      s"""s'''first line
         |   |    second line$Caret'''.stripMargin
         |""".stripMargin
    doTestMultilineForAnyInsertMarginSetting(from, to, after)
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
    doTestMultilineForAnyInsertMarginSetting(from, to, after)
  }

  def testToEmptyInterpMultilineString_WithMargin(): Unit = {
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
    doTestMultilineForAnyInsertMarginSetting(from, to, after)
  }

  // paste to non-empty multiline string
  def testToNonEmptyOneLineMultilineString(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = false
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""'''abc $Caret'''
         |""".stripMargin
    val after =
      s"""'''abc first line
         |    second line$Caret'''
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToNonEmptyOneLineMultilineString_1(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""'''abc $Caret'''
         |""".stripMargin
    val after =
      s"""'''abc first line
         |  |    second line$Caret'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToNonEmptyOneLineMultilineString_WithMargin(): Unit = {
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""'''abc $Caret'''.stripMargin
         |""".stripMargin
    val after =
      s"""'''abc first line
         |  |    second line$Caret'''.stripMargin
         |""".stripMargin
    doTestMultilineForAnyInsertMarginSetting(from, to, after)
  }

  def testToNonEmptyInterpOneLineMultilineString(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = false
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""s'''abc $Caret'''
         |""".stripMargin
    val after =
      s"""s'''abc first line
         |    second line$Caret'''
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToNonEmptyInterpOneLineMultilineString_1(): Unit = {
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""s'''abc $Caret'''
         |""".stripMargin
    val after =
      s"""s'''abc first line
         |   |    second line$Caret'''.stripMargin
         |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testToNonEmptyInterpOneLineMultilineString_WithMargin(): Unit = {
    val from =
      s"""${Start}first line
         |    second line$End
         |""".stripMargin
    val to =
      s"""s'''abc $Caret'''.stripMargin
         |""".stripMargin
    val after =
      s"""s'''abc first line
         |   |    second line$Caret'''.stripMargin
         |""".stripMargin
    doTestMultilineForAnyInsertMarginSetting(from, to, after)
  }

  // paste one-line content
  def testOneLineTextToEmptyOneLineMultilineString(): Unit = {
    val from =
      s"""${Start}first line$End
         |""".stripMargin
    val to =
      s"""'''$Caret'''
         |""".stripMargin
    val after =
      s"""'''first line$Caret'''
         |""".stripMargin
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = false
    doTestMultiline(from, to, after)
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    doTestMultiline(from, to, after)
  }

  def testOneLineTextToNonEmptyOneLineMultilineString(): Unit = {
    val from =
      s"""${Start}first line$End
         |""".stripMargin
    val to =
      s"""'''abc $Caret def'''
         |""".stripMargin
    val after =
      s"""'''abc first line$Caret def'''
         |""".stripMargin
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = false
    doTestMultiline(from, to, after)
    getScalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    doTestMultiline(from, to, after)
  }

  //SCL-20646
  def testCopyMultilineStringAndPasteAfterAssign_WithSomeContent(): Unit = {
    val from =
      s"""val a = 1
         |
         |val testSource =
         |  $Start${Caret}s'''Test
         |    |  a = $${a}
         |    |  b = $${b}
         |    |  c = $${c}
         |    |'''.stripMargin$End
         |""".stripMargin

    val to =
      s"""val testTarget = $Start$Caret"This string will be replaced with 'testSource'"$End
         |""".stripMargin

    val after =
      """val testTarget = s'''Test
        |                    |  a = ${a}
        |                    |  b = ${b}
        |                    |  c = ${c}
        |                    |'''.stripMargin
        |""".stripMargin
    doTestMultiline(from, to, after)
  }

  def testCopyMultilineStringAndPasteAfterAssign_WithSomeContent_NonString(): Unit = {
    val from =
      s"""val a = 1
         |
         |val testSource = $Start${Caret}s'''Test
         |                   |  a = $${a}
         |                   |  b = $${b}
         |                   |  c = $${c}
         |                   |'''.stripMargin$End
         |""".stripMargin

    val to =
      s"""val testTarget = $Start${Caret}42$End
         |""".stripMargin

    val after =
      """val testTarget = s'''Test
        |                    |  a = ${a}
        |                    |  b = ${b}
        |                    |  c = ${c}
        |                    |'''.stripMargin
        |""".stripMargin
    doTestMultiline(from, to, after)
  }

  //SCL-20646
  def testCopyMultilineStringAndPasteAfterAssign_SmallerExample(): Unit = {
    val from =
      s"""val s =
         |  ${Start}s'''$$println
         |     |'''$End
         |""".stripMargin

    val to =
      s"""val t = $Caret
         |""".stripMargin

    val after =
      """val t = s'''$println
        |           |'''
        |""".stripMargin
    doTestMultiline(from, to, after)
  }
}
