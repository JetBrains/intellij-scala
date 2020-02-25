package org.jetbrains.plugins.scala.editor.selectioner

class ScalaStringLiteralSelectionerTest extends ExtendWordSelectionHandlerTestBase {

  protected val q   = "\""
  protected val qq  = "\"\""
  protected val qqq = "\"\"\""

  def testString(): Unit =
    doTest(Seq(
      s""""single 42$Caret line"""",
      s""""single ${Start}42$End$Caret line"""",
      s""""${Start}single 42$Caret line$End"""",
      s"""$Start"single 42$Caret line"$End""",
    ))

  def testString_Empty(): Unit =
    doTest(Seq(
      s""""$Caret"""",
      s"""$Start"$Caret"$End""",
    ))

  def testMultilineString(): Unit =
    doTest(Seq(
      s"""${qqq}line 1
         |  |line 2$Caret
         |  |$qqq.stripMargin""".stripMargin,

      s"""${qqq}line 1
         |  |line ${Start}2$End$Caret
         |  |$qqq.stripMargin""".stripMargin,

      s"""$qqq${Start}line 1
         |  |line 2$Caret
         |  |$End$qqq.stripMargin""".stripMargin,

      s"""$Start${qqq}line 1
         |  |line 2$Caret
         |  |$qqq$End.stripMargin""".stripMargin,

      s"""$Start${qqq}line 1
         |  |line 2$Caret
         |  |$qqq.stripMargin$End""".stripMargin,
    ))

  def testMultilineString_Empty(): Unit =
    doTest(Seq(
      s"""$qqq$Caret$qqq""",
      s"""$q$Start$qq$Caret$qq$End$q""", // I don't know whether this step this was a feature or bug, just fix the behaviour in tests
      s"""$Start$qqq$Caret$qqq$End"""
    ))

  def testMultilineString_Empty_WithSpaces(): Unit =
    doTest(Seq(
      s"""$qqq $Caret$qqq""",
      s"""$qqq$Start $Caret$End$qqq""",
      s"""$Start$qqq $Caret$qqq$End"""
    ))

  def testMultilineString_CaretBeforeString(): Unit =
    doTest(Seq(
      s"""$Caret$qqq content $qqq""",
      s"""$Caret$Start$qqq content $qqq$End""",
    ))

  def testMultilineString_Empty_CaretAtTheVeryEndOfContent(): Unit =
    doTest(Seq(
      s"""$qqq $Caret$qqq""",
      s"""$qqq$Start $Caret$End$qqq""",
    ))

  def testMultilineString_CaretInsideOpeningQuotes(): Unit =
    doTest(Seq(
      s"""$q$Caret$qq content $qqq""",
      s"""$Start$q$Caret$qq content $qqq$End""",
    ))

  def testMultilineString_CaretInsideClosingQuotes(): Unit =
    doTest(Seq(
      s"""$qqq content $q$Caret$qq""",
      s"""$Start$qqq content $q$Caret$qq$End""",
    ))

  //
  // Interpolated
  //

  def testInterpolatedString(): Unit =
    doTest(Seq(
      s"""s"single 42$Caret line"""",
      s"""s"single ${Start}42$End$Caret line"""",
      s"""s"${Start}single 42$Caret line$End"""",
      s"""${Start}s"single 42$Caret line"$End""",
    ))

  def testInterpolatedString_Empty(): Unit =
    doTest(Seq(
      s"""s"$Caret"""",
      s"""${Start}s"$Caret"$End""",
    ))

  def testInterpolatedMultilineString(): Unit =
    doTest(Seq(
      s"""s${qqq}line 1
         |   |line 2$Caret
         |   |$qqq.stripMargin""".stripMargin,

      s"""s${qqq}line 1
         |   |line ${Start}2$End$Caret
         |   |$qqq.stripMargin""".stripMargin,

      s"""s$qqq${Start}line 1
         |   |line 2$Caret
         |   |$End$qqq.stripMargin""".stripMargin,

      s"""${Start}s${qqq}line 1
         |   |line 2$Caret
         |   |$qqq$End.stripMargin""".stripMargin,

      s"""${Start}s${qqq}line 1
         |   |line 2$Caret
         |   |$qqq.stripMargin$End""".stripMargin,
    ))

  def testInterpolatedMultilineString_Empty(): Unit =
    doTest(Seq(
      s"""s$qqq$Caret$qqq""",
      s"""${Start}s$qqq$Caret$qqq$End"""
    ))

  def testInterpolatedMultilineString_SingleLine(): Unit =
    doTest(Seq(
      s"""s${qqq}line 1$Caret same line$qqq.stripMargin""",
      s"""s${qqq}line ${Start}1$End$Caret same line$qqq.stripMargin""",
      s"""s$qqq${Start}line 1$Caret same line$End$qqq.stripMargin""",
      s"""${Start}s${qqq}line 1$Caret same line$qqq$End.stripMargin""",
    ))

  def testInterpolatedMultilineString_Empty_CaretInsideWhitespace(): Unit =
    doTest(Seq(
      s"""s$qqq $Caret $qqq""",
      s"""s$qqq$Start $Caret $End$qqq""",
      s"""${Start}s$qqq $Caret $qqq$End""",
    ))

  def testInterpolatedMultilineString_Empty_CaretAtTheVeryEndOfContent(): Unit =
    doTest(Seq(
      s"""s$qqq $Caret$qqq""",
      s"""s$qqq$Start $Caret$End$qqq""",
    ))

  def testInterpolatedMultilineString_CaretInsideOpeningQuotes(): Unit =
    doTest(Seq(
      s"""s$q$Caret$qq content $qqq""",
      s"""${Start}s$q$Caret$qq content $qqq$End""",
    ))

  def testInterpolatedMultilineString_CaretInsideClosingQuotes(): Unit =
    doTest(Seq(
      s"""s$qqq content $q$Caret$qq""",
      s"""${Start}s$qqq content $q$Caret$qq$End""",
    ))

  //
  // Interpolated with injections
  //

  private def doTestForMultilineAndSingleLine(editorTextStates: Seq[String]): Unit ={
    doTest(editorTextStates)
    val editorTextStatesWithSingleLine = editorTextStates.map(_.replace(qqq, q))
    doTest(editorTextStatesWithSingleLine)
  }

  def testInterpolatedString_SimpleInjection_BeforeInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some ${Caret}content $$value $qqq""",
      s"""s$qqq some $Start${Caret}content$End $$value $qqq""",
      s"""s$qqq$Start some ${Caret}content $$value $End$qqq""",
      s"""${Start}s$qqq some ${Caret}content $$value $qqq$End""",
    ))

  def testInterpolatedString_SimpleInjection_AfterInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $$value extra ${Caret}content 42$qqq""",
      s"""s$qqq some content $$value extra $Start${Caret}content$End 42$qqq""",
      s"""s$qqq$Start some content $$value extra ${Caret}content 42$End$qqq""",
      s"""${Start}s$qqq some content $$value extra ${Caret}content 42$qqq$End""",
    ))

  def testInterpolatedString_SimpleInjection_InsideInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $$va${Caret}lue $qqq""",
      s"""s$qqq some content $$${Start}va${Caret}lue$End $qqq""",
      s"""s$qqq some content $Start$$va${Caret}lue$End $qqq""",
    ))

  def testInterpolatedString_SimpleInjection_RightBeforeInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $Caret$$value 42$qqq""",
      s"""s$qqq some content $Caret$Start$$value$End 42$qqq""",
      s"""s$qqq$Start some content $Caret$$value 42$End$qqq""",
      s"""${Start}s$qqq some content $Caret$$value 42$qqq$End""",
    ))

  def testInterpolatedString_SimpleInjection_RightAfterInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $$value$Caret 42$qqq""",
      s"""s$qqq some content $$${Start}value$Caret$End 42$qqq""",
      s"""s$qqq some content $Start$$value$Caret$End 42$qqq""",
      s"""s$qqq$Start some content $$value$Caret 42$End$qqq""",
      s"""${Start}s$qqq some content $$value$Caret 42$qqq$End""",
    ))

  def testInterpolatedString_BlockInjection_InsideInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $${${Caret}value + 42} extra content$qqq""",
      s"""s$qqq some content $${${Caret}${Start}value$End + 42} extra content$qqq""",
      s"""s$qqq some content $${${Caret}${Start}value + 42$End} extra content$qqq""",
      s"""s$qqq some content $$${Start}{${Caret}value + 42}$End extra content$qqq""",
      s"""s$qqq some content ${Start}$${${Caret}value + 42}$End extra content$qqq""",
    ))

  def testInterpolatedString_BlockInjection_RightBeforeInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $Caret$${value + 42} extra content$qqq""",
      s"""s$qqq some content ${Start}$Caret$${value + 42}$End extra content$qqq""",
      s"""s$qqq${Start} some content $Caret$${value + 42} extra content$End$qqq""",
      s"""${Start}s$qqq some content $Caret$${value + 42} extra content$qqq$End""",
    ))

  def testInterpolatedString_BlockInjection_RightAfterInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $${value + 42}$Caret extra content$qqq""",
      s"""s$qqq some content $${value + 42$Start}$Caret$End extra content$qqq""",
      s"""s$qqq some content $$$Start{value + 42}$Caret$End extra content$qqq""",
      s"""s$qqq some content $Start$${value + 42}$Caret$End extra content$qqq""",
      s"""s$qqq$Start some content $${value + 42}$Caret extra content$End$qqq""",
      s"""${Start}s$qqq some content $${value + 42}$Caret extra content$qqq$End""",
    ))

  def testInterpolatedString_BlockInjection_BetweenInjectionAndBlock(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $$$Caret{value + 42} extra content$qqq""",
      s"""s$qqq some content $$$Caret$Start{value + 42}$End extra content$qqq""",
      s"""s$qqq some content $Start$$$Caret{value + 42}$End extra content$qqq""",
      s"""s$qqq$Start some content $$$Caret{value + 42} extra content$End$qqq""",
      s"""${Start}s$qqq some content $$$Caret{value + 42} extra content$qqq$End""",
    ))
}
