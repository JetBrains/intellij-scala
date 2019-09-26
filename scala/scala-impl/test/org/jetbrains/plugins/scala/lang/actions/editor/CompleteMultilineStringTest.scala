package org.jetbrains.plugins.scala.lang.actions.editor

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class CompleteMultilineStringTest extends EditorActionTestBase {

  private val Quote: String = "\""
  private val Quotes: String = "\"\"\""

  private def doTest(before: String, after: String): Unit = {
    checkGeneratedTextAfterTyping(before, after, '\"')
  }

  def testComplete(): Unit = {
    val before =
      s"""class A {
         |  \"\"$CARET
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  $Quotes$CARET$Quotes
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteInEmptyFile(): Unit = {
    val before = s"""\"\"$CARET""".stripMargin
    val after = s"""$Quotes$CARET$Quotes""".stripMargin
    doTest(before, after)
  }

  def testCompleteInEmptyFile_1(): Unit = {
    val before = s"""   \"\"$CARET""".stripMargin
    val after = s"""   $Quotes$CARET$Quotes""".stripMargin
    doTest(before, after)
  }

  def testCompleteInEmptyFile_2(): Unit = {
    val before = s"""\"\"$CARET    """.stripMargin
    val after = s"""$Quotes$CARET$Quotes    """.stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileBeginning(): Unit = {
    val before =
      s"""\"\"$CARET
         |val x = 42
         |""".stripMargin
    val after =
      s"""$Quotes$CARET$Quotes
         |val x = 42
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileBeginning_1(): Unit = {
    val before =
      s"""  \"\"$CARET
         |val x = 42
         |""".stripMargin
    val after =
      s"""  $Quotes$CARET$Quotes
         |val x = 42
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileEnding(): Unit = {
    val before =
      s"""val x = 42
         |\"\"$CARET
         |""".stripMargin
    val after =
      s"""val x = 42
         |$Quotes$CARET$Quotes
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileEnding_1(): Unit = {
    val before =
      s"""val x = 42
         |    \"\"$CARET
         |""".stripMargin
    val after =
      s"""val x = 42
         |    $Quotes$CARET$Quotes
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileEnding_2(): Unit = {
    val before =
      s"""val x = 42
         |\"\"$CARET""".stripMargin
    val after =
      s"""val x = 42
         |$Quotes$CARET$Quotes""".stripMargin
    doTest(before, after)
  }

  def testCompleteMultiCaret(): Unit = {
    val before =
      s"""Some($CARET)
         |Some($CARET)
         |Some($CARET)
         |""".stripMargin
    val afterTyped1 =
      s"""Some($Quote$CARET$Quote)
         |Some($Quote$CARET$Quote)
         |Some($Quote$CARET$Quote)
         |""".stripMargin
    val afterTyped2 =
      s"""Some($Quote$Quote$CARET)
         |Some($Quote$Quote$CARET)
         |Some($Quote$Quote$CARET)
         |""".stripMargin
    val afterTyped3 =
      s"""Some($Quotes$CARET$Quotes)
         |Some($Quotes$CARET$Quotes)
         |Some($Quotes$CARET$Quotes)
         |""".stripMargin
    doTest(before, afterTyped1)
    doTest(afterTyped1, afterTyped2)
    doTest(afterTyped2, afterTyped3)
  }

  def testCompleteMultiCaret_InPresenceOfAnotherMultilineString(): Unit = {
    val before =
      s"""Some($CARET)
         |Some($Quotes$Quotes)
         |""".stripMargin
    val afterTyped1 =
      s"""Some($Quote$CARET$Quote)
         |Some($Quotes$Quotes)
         |""".stripMargin
    val afterTyped2 =
      s"""Some($Quote$Quote$CARET)
         |Some($Quotes$Quotes)
         |""".stripMargin
    val afterTyped3 =
      s"""Some($Quotes$CARET$Quotes)
         |Some($Quotes$Quotes)
         |""".stripMargin
    doTest(before, afterTyped1)
    doTest(afterTyped1, afterTyped2)
    doTest(afterTyped2, afterTyped3)
  }

  def testCompleteMultiCaret_Interpolated(): Unit = {
    val before =
      s"""Some(s$CARET)
         |Some(s$CARET)
         |Some(s$CARET)
         |""".stripMargin
    val afterTyped1 =
      s"""Some(s$Quote$CARET$Quote)
         |Some(s$Quote$CARET$Quote)
         |Some(s$Quote$CARET$Quote)
         |""".stripMargin
    val afterTyped2 =
      s"""Some(s$Quote$Quote$CARET)
         |Some(s$Quote$Quote$CARET)
         |Some(s$Quote$Quote$CARET)
         |""".stripMargin
    val afterTyped3 =
      s"""Some(s$Quotes$CARET$Quotes)
         |Some(s$Quotes$CARET$Quotes)
         |Some(s$Quotes$CARET$Quotes)
         |""".stripMargin
    doTest(before, afterTyped1)
    doTest(afterTyped1, afterTyped2)
    doTest(afterTyped2, afterTyped3)
  }

  def testCompleteMultiCaret_EmptyFile(): Unit = {
    val before =
      s"""$CARET
         |$CARET
         |$CARET""".stripMargin
    val afterTyped1 =
      s"""$Quote$CARET$Quote
         |$Quote$CARET$Quote
         |$Quote$CARET$Quote""".stripMargin
    val afterTyped2 =
      s"""$Quote$Quote$CARET
         |$Quote$Quote$CARET
         |$Quote$Quote$CARET""".stripMargin
    val afterTyped3 =
      s"""$Quotes$CARET$Quotes
         |$Quotes$CARET$Quotes
         |$Quotes$CARET$Quotes""".stripMargin
    doTest(before, afterTyped1)
    doTest(afterTyped1, afterTyped2)
    doTest(afterTyped2, afterTyped3)
  }

  def testCompleteMultiCaret_EmptyFileWithEmptyEndLine(): Unit = {
    val before =
      s"""$CARET
         |$CARET
         |""".stripMargin
    val afterTyped1 =
      s"""$Quote$CARET$Quote
         |$Quote$CARET$Quote
         |""".stripMargin
    val afterTyped2 =
      s"""$Quote$Quote$CARET
         |$Quote$Quote$CARET
         |""".stripMargin
    val afterTyped3 =
      s"""$Quotes$CARET$Quotes
         |$Quotes$CARET$Quotes
         |""".stripMargin
    doTest(before, afterTyped1)
    doTest(afterTyped1, afterTyped2)
    doTest(afterTyped2, afterTyped3)
  }

  def testCompleteMultiCaret_Interpolated_EmptyFile(): Unit = {
    val before =
      s"""s$CARET
         |s$CARET
         |s$CARET""".stripMargin
    val afterTyped1 =
      s"""s$Quote$CARET$Quote
         |s$Quote$CARET$Quote
         |s$Quote$CARET$Quote""".stripMargin
    val afterTyped2 =
      s"""s$Quote$Quote$CARET
         |s$Quote$Quote$CARET
         |s$Quote$Quote$CARET""".stripMargin
    val afterTyped3 =
      s"""s$Quotes$CARET$Quotes
         |s$Quotes$CARET$Quotes
         |s$Quotes$CARET$Quotes""".stripMargin
    doTest(before, afterTyped1)
    doTest(afterTyped1, afterTyped2)
    doTest(afterTyped2, afterTyped3)
  }
}
