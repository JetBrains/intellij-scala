package org.jetbrains.plugins.scala.lang.actions.editor

class SpaceInsertTest extends EditorTypeActionTestBase {

  override protected def typedChar: Char = ' '

  def testIfElse(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    if (true) 8
         |  else$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x =
         |    if (true) 8
         |    else $CARET
         |}""".stripMargin
    )

  def testIfElse_1(): Unit = {
    getScalaCodeStyleSettings.ALIGN_IF_ELSE = true
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x = if (true) 8
         |  else$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x = if (true) 8
         |          else $CARET
         |}""".stripMargin
    )
  }

  def testIfElse_EndOfTheFile(): Unit =
    doTestWithEmptyLastLine(
      s"""val x =
         |  if (true) 8
         | else$CARET
         |""".stripMargin,
      s"""val x =
         |  if (true) 8
         |  else $CARET
         |""".stripMargin
    )

  def testIfElse_EndOfTheFile_ElseAtLeftMostPosition(): Unit =
    doTestWithEmptyLastLine(
      s"""val x =
         |  if (true) 8
         |else$CARET
         |""".stripMargin,
      s"""val x =
         |  if (true) 8
         |  else $CARET
         |""".stripMargin
    )

  def testMatchCase(): Unit =
    doTestWithEmptyLastLine(
      s"""val x = 5 match {
         |  case 1 => 2
         |    case$CARET
         |}""".stripMargin,
      s"""val x = 5 match {
         |  case 1 => 2
         |  case $CARET
         |}""".stripMargin
    )

  def testTryCatch(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    try ()
         |  catch$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x =
         |    try ()
         |    catch $CARET
         |}""".stripMargin
    )

  def testTryFinally(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    try ()
         |  finally$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x =
         |    try ()
         |    finally $CARET
         |}""".stripMargin
    )

  def testAfterIncompleteInfixOperatorInFunctionBody(): Unit =
    checkGeneratedTextAfterTypingText(
      s"""class B {
         |  def foo = 42 +
         |  $CARET
         |}""".stripMargin,
      s"""class B {
         |  def foo = 42 +
         |      $CARET
         |}""".stripMargin,
      "    "
    )
}
