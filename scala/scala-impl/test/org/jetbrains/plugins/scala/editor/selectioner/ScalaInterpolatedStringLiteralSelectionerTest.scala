package org.jetbrains.plugins.scala.editor.selectioner

//noinspection RedundantBlock
class ScalaInterpolatedStringLiteralSelectionerTest extends ScalaStringLiteralSelectionerBaseTest {

  def testSimple(): Unit =
    doTest(Seq(
      s"""s"single 42$Caret line"""",
      s"""s"single ${Start}42$End$Caret line"""",
      s"""s"${Start}single 42$Caret line$End"""",
      s"""${Start}s"single 42$Caret line"$End""",
    ))

  def testEmpty(): Unit =
    doTest(Seq(
      s"""s"$Caret"""",
      s"""${Start}s"$Caret"$End""",
    ))

  def testMultiline(): Unit =
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

  def testMultiline_Empty(): Unit =
    doTest(Seq(
      s"""s$qqq$Caret$qqq""",
      s"""${Start}s$qqq$Caret$qqq$End"""
    ))

  def testMultiline_SingleLine(): Unit =
    doTest(Seq(
      s"""s${qqq}line 1$Caret same line$qqq.stripMargin""",
      s"""s${qqq}line ${Start}1$End$Caret same line$qqq.stripMargin""",
      s"""s$qqq${Start}line 1$Caret same line$End$qqq.stripMargin""",
      s"""${Start}s${qqq}line 1$Caret same line$qqq$End.stripMargin""",
    ))

  def testMultiline_Empty_CaretInsideWhitespace(): Unit =
    doTest(Seq(
      s"""s$qqq $Caret $qqq""",
      s"""s$qqq$Start $Caret $End$qqq""",
      s"""${Start}s$qqq $Caret $qqq$End""",
    ))

  def testMultiline_Empty_CaretAtTheVeryEndOfContent(): Unit =
    doTest(Seq(
      s"""s$qqq $Caret$qqq""",
      s"""s$qqq$Start $Caret$End$qqq""",
    ))

  def testMultiline_CaretInsideOpeningQuotes(): Unit =
    doTest(Seq(
      s"""s$q$Caret$qq content $qqq""",
      s"""${Start}s$q$Caret$qq content $qqq$End""",
    ))

  def testMultiline_CaretInsideClosingQuotes(): Unit =
    doTest(Seq(
      s"""s$qqq content $q$Caret$qq""",
      s"""${Start}s$qqq content $q$Caret$qq$End""",
    ))

  //
  // Interpolated with injections
  //

  def testSimpleInjection_BeforeInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some ${Caret}content $$value $qqq""",
      s"""s$qqq some $Start${Caret}content$End $$value $qqq""",
      s"""s$qqq$Start some ${Caret}content $$value $End$qqq""",
      s"""${Start}s$qqq some ${Caret}content $$value $qqq$End""",
    ))

  def testSimpleInjection_AfterInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $$value extra ${Caret}content 42$qqq""",
      s"""s$qqq some content $$value extra $Start${Caret}content$End 42$qqq""",
      s"""s$qqq$Start some content $$value extra ${Caret}content 42$End$qqq""",
      s"""${Start}s$qqq some content $$value extra ${Caret}content 42$qqq$End""",
    ))

  def testSimpleInjection_InsideInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $$va${Caret}lue $qqq""",
      s"""s$qqq some content $$${Start}va${Caret}lue$End $qqq""",
      s"""s$qqq some content $Start$$va${Caret}lue$End $qqq""",
    ))

  def testSimpleInjection_RightBeforeInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $Caret$$value 42$qqq""",
      s"""s$qqq some content $Caret$Start$$value$End 42$qqq""",
      s"""s$qqq$Start some content $Caret$$value 42$End$qqq""",
      s"""${Start}s$qqq some content $Caret$$value 42$qqq$End""",
    ))

  def testSimpleInjection_RightAfterInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $$value$Caret 42$qqq""",
      s"""s$qqq some content $$${Start}value$Caret$End 42$qqq""",
      s"""s$qqq some content $Start$$value$Caret$End 42$qqq""",
      s"""s$qqq$Start some content $$value$Caret 42$End$qqq""",
      s"""${Start}s$qqq some content $$value$Caret 42$qqq$End""",
    ))

  def testBlockInjection_InsideInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $${${Caret}value + 42} extra content$qqq""",
      s"""s$qqq some content $${${Caret}${Start}value$End + 42} extra content$qqq""",
      s"""s$qqq some content $${${Caret}${Start}value + 42$End} extra content$qqq""",
      s"""s$qqq some content $$${Start}{${Caret}value + 42}$End extra content$qqq""",
      s"""s$qqq some content ${Start}$${${Caret}value + 42}$End extra content$qqq""",
    ))

  def testBlockInjection_RightBeforeInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $Caret$${value + 42} extra content$qqq""",
      s"""s$qqq some content ${Start}$Caret$${value + 42}$End extra content$qqq""",
      s"""s$qqq${Start} some content $Caret$${value + 42} extra content$End$qqq""",
      s"""${Start}s$qqq some content $Caret$${value + 42} extra content$qqq$End""",
    ))

  def testBlockInjection_RightAfterInjection(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $${value + 42}$Caret extra content$qqq""",
      s"""s$qqq some content $${value + 42$Start}$Caret$End extra content$qqq""",
      s"""s$qqq some content $$$Start{value + 42}$Caret$End extra content$qqq""",
      s"""s$qqq some content $Start$${value + 42}$Caret$End extra content$qqq""",
      s"""s$qqq$Start some content $${value + 42}$Caret extra content$End$qqq""",
      s"""${Start}s$qqq some content $${value + 42}$Caret extra content$qqq$End""",
    ))

  def testBlockInjection_BetweenInjectionAndBlock(): Unit =
    doTestForMultilineAndSingleLine(Seq(
      s"""s$qqq some content $$$Caret{value + 42} extra content$qqq""",
      s"""s$qqq some content $$$Caret$Start{value + 42}$End extra content$qqq""",
      s"""s$qqq some content $Start$$$Caret{value + 42}$End extra content$qqq""",
      s"""s$qqq$Start some content $$$Caret{value + 42} extra content$End$qqq""",
      s"""${Start}s$qqq some content $$$Caret{value + 42} extra content$qqq$End""",
    ))
}
