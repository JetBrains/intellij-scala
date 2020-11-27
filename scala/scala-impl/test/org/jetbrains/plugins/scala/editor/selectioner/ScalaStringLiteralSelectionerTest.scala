package org.jetbrains.plugins.scala.editor.selectioner

//noinspection RedundantBlock
class ScalaStringLiteralSelectionerTest extends ScalaStringLiteralSelectionerBaseTest {

  def test_Simple(): Unit =
    doTest(Seq(
      s""""single 42$Caret line"""",
      s""""single ${Start}42$End$Caret line"""",
      s""""${Start}single 42$Caret line$End"""",
      s"""$Start"single 42$Caret line"$End""",
    ))

  def test_Empty(): Unit =
    doTest(Seq(
      s""""$Caret"""",
      s"""$Start"$Caret"$End""",
    ))

  def testMultiline(): Unit =
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

  def testMultiline_Empty(): Unit =
    doTest(Seq(
      s"""$qqq$Caret$qqq""",
      s"""$q$Start$qq$Caret$qq$End$q""", // I don't know whether this step this was a feature or bug, just fix the behaviour in tests
      s"""$Start$qqq$Caret$qqq$End"""
    ))

  def testMultiline_Empty_WithSpaces(): Unit =
    doTest(Seq(
      s"""$qqq $Caret$qqq""",
      s"""$qqq$Start $Caret$End$qqq""",
      s"""$Start$qqq $Caret$qqq$End"""
    ))

  def testMultiline_CaretBeforeString(): Unit =
    doTest(Seq(
      s"""$Caret$qqq content $qqq""",
      s"""$Caret$Start$qqq content $qqq$End""",
    ))

  def testMultiline_Empty_CaretAtTheVeryEndOfContent(): Unit =
    doTest(Seq(
      s"""$qqq $Caret$qqq""",
      s"""$qqq$Start $Caret$End$qqq""",
    ))

  def testMultiline_CaretInsideOpeningQuotes(): Unit =
    doTest(Seq(
      s"""$q$Caret$qq content $qqq""",
      s"""$Start$q$Caret$qq content $qqq$End""",
    ))

  def testMultiline_CaretInsideClosingQuotes(): Unit =
    doTest(Seq(
      s"""$qqq content $q$Caret$qq""",
      s"""$Start$qqq content $q$Caret$qq$End""",
    ))

  /** ensure [[com.intellij.codeInsight.editorActions.wordSelection.InjectedFileReferenceSelectioner]] works */
  def testFilePathPart(): Unit = {
    doTestForMultilineAndSingleLine(Seq(
     s"${qqq}aaaa/bbb/ccc$Caret/ddd${qqq}",
     s"${qqq}aaaa/bbb/${Start}ccc$Caret${End}/ddd${qqq}",
     s"${qqq}aaaa/${Start}bbb/ccc$Caret${End}/ddd${qqq}",
     s"${qqq}${Start}aaaa/bbb/ccc$Caret${End}/ddd${qqq}",
     s"${qqq}${Start}aaaa/bbb/ccc$Caret/ddd${End}${qqq}",
    ))
  }

  def testFilePathPart_1(): Unit = {
    doTestForMultilineAndSingleLine(Seq(
     s"${qqq}aaaa/bbb.ccc$Caret.ddd/eee${qqq}",
     s"${qqq}aaaa/bbb.${Start}ccc$Caret${End}.ddd/eee${qqq}",
     s"${qqq}aaaa/${Start}bbb.ccc$Caret.ddd${End}/eee${qqq}",
     s"${qqq}${Start}aaaa/bbb.ccc$Caret.ddd${End}/eee${qqq}",
     s"${qqq}${Start}aaaa/bbb.ccc$Caret.ddd/eee$${End}{qqq}",
    ))
  }
}
