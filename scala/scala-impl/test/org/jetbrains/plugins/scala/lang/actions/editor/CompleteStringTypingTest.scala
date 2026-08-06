package org.jetbrains.plugins.scala.lang.actions.editor

class CompleteStringTypingTest  extends EditorTypeActionTestBase {

  override protected def typedChar: Char = '"'

  def testComplete(): Unit = {
    val before =
      s"""class A {
         |  $qq$CARET
         |}
         |""".stripMargin
    val after =
      s"""class A {
         |  $qqq$CARET$qqq
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteInEmptyFile(): Unit = {
    val before = s"""$qq$CARET""".stripMargin
    val after = s"""$qqq$CARET$qqq""".stripMargin
    doTestWithEmptyLastLine(before, after)
  }

  def testCompleteInEmptyFile_1(): Unit = {
    val before = s"""   $qq$CARET""".stripMargin
    val after = s"""   $qqq$CARET$qqq""".stripMargin
    doTestWithEmptyLastLine(before, after)
  }

  def testCompleteInEmptyFile_2(): Unit = {
    val before = s"""$qq$CARET    """.stripMargin
    val after = s"""$qqq$CARET$qqq    """.stripMargin
    doTestWithEmptyLastLine(before, after)
  }

  def testCompleteAtTheFileBeginning(): Unit = {
    val before =
      s"""$qq$CARET
         |val x = 42
         |""".stripMargin
    val after =
      s"""$qqq$CARET$qqq
         |val x = 42
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileBeginning_1(): Unit = {
    val before =
      s"""  $qq$CARET
         |val x = 42
         |""".stripMargin
    val after =
      s"""  $qqq$CARET$qqq
         |val x = 42
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileEnding(): Unit = {
    val before =
      s"""val x = 42
         |$qq$CARET
         |""".stripMargin
    val after =
      s"""val x = 42
         |$qqq$CARET$qqq
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileEnding_1(): Unit = {
    val before =
      s"""val x = 42
         |    $qq$CARET
         |""".stripMargin
    val after =
      s"""val x = 42
         |    $qqq$CARET$qqq
         |""".stripMargin
    doTest(before, after)
  }

  def testCompleteAtTheFileEnding_2(): Unit = {
    val before =
      s"""val x = 42
         |$qq$CARET""".stripMargin
    val after =
      s"""val x = 42
         |$qqq$CARET$qqq""".stripMargin
    doTest(before, after)
  }

  def testCompleteMultiCaret(): Unit = doRepetitiveTypingTest(
    s"""Some($CARET)
       |Some($CARET)
       |Some($CARET)
       |""".stripMargin,
    s"""Some($q$CARET$q)
       |Some($q$CARET$q)
       |Some($q$CARET$q)
       |""".stripMargin,
    s"""Some($q$q$CARET)
       |Some($q$q$CARET)
       |Some($q$q$CARET)
       |""".stripMargin,
    s"""Some($qqq$CARET$qqq)
       |Some($qqq$CARET$qqq)
       |Some($qqq$CARET$qqq)
       |""".stripMargin
  )

  def testCompleteMultiCaret_InPresenceOfAnotherMultilineString(): Unit = doRepetitiveTypingTest(
    s"""Some($CARET)
       |Some($qqq$qqq)
       |""".stripMargin,
    s"""Some($q$CARET$q)
       |Some($qqq$qqq)
       |""".stripMargin,
    s"""Some($q$q$CARET)
       |Some($qqq$qqq)
       |""".stripMargin,
    s"""Some($qqq$CARET$qqq)
       |Some($qqq$qqq)
       |""".stripMargin
  )

  def testCompleteMultiCaret_Interpolated(): Unit = doRepetitiveTypingTest(
      s"""Some(s$CARET)
         |Some(s$CARET)
         |Some(s$CARET)
         |""".stripMargin,
      s"""Some(s$q$CARET$q)
         |Some(s$q$CARET$q)
         |Some(s$q$CARET$q)
         |""".stripMargin,
      s"""Some(s$q$q$CARET)
         |Some(s$q$q$CARET)
         |Some(s$q$q$CARET)
         |""".stripMargin,
      s"""Some(s$qqq$CARET$qqq)
         |Some(s$qqq$CARET$qqq)
         |Some(s$qqq$CARET$qqq)
         |""".stripMargin
  )

  def testCompleteMultiCaret_EmptyFile(): Unit = doRepetitiveTypingTest(
    s"""$CARET
       |$CARET
       |$CARET""".stripMargin,
    s"""$q$CARET$q
       |$q$CARET$q
       |$q$CARET$q""".stripMargin,
    s"""$q$q$CARET
       |$q$q$CARET
       |$q$q$CARET""".stripMargin,
    s"""$qqq$CARET$qqq
       |$qqq$CARET$qqq
       |$qqq$CARET$qqq""".stripMargin
  )

  def testCompleteMultiCaret_Interpolated_EmptyFile(): Unit = doRepetitiveTypingTest(
    s"""s$CARET
       |s$CARET
       |s$CARET""".stripMargin,
    s"""s$q$CARET$q
       |s$q$CARET$q
       |s$q$CARET$q""".stripMargin,
    s"""s$q$q$CARET
       |s$q$q$CARET
       |s$q$q$CARET""".stripMargin,
    s"""s$qqq$CARET$qqq
       |s$qqq$CARET$qqq
       |s$qqq$CARET$qqq""".stripMargin
  )

  def testCompleteStringInSbtFile(): Unit = doTest(
    s"""name := $CARET""",
    s"""name := $q$CARET$q""",
    "build.sbt"
  )

  def testCompleteStringInWorksheetFile(): Unit = doTest(
    s"""val foo = $CARET""",
    s"""val foo = $q$CARET$q""",
    "worksheet.sc"
  )
}
