package org.jetbrains.plugins.scala.lang.actions.editor

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class CompleteMultilineStringTest extends EditorActionTestBase {
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
}
