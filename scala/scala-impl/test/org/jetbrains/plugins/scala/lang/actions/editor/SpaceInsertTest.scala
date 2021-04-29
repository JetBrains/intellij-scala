package org.jetbrains.plugins.scala.lang.actions.editor

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class SpaceInsertTest extends EditorActionTestBase {

  private def doTest(before: String, after: String): Unit =
    checkGeneratedTextAfterTyping(before, after, ' ')

  def testIfElse(): Unit = {
    val before =
      s"""def test = {
         |  val x =
         |    if (true) 8
         |  else$CARET
         |}""".stripMargin
    val after =
      s"""def test = {
         |  val x =
         |    if (true) 8
         |    else $CARET
         |}""".stripMargin
    doTest(before, after)
  }

  def testIfElse_1(): Unit = {
    getScalaSettings.ALIGN_IF_ELSE = true
    val before =
      s"""def test = {
         |  val x = if (true) 8
         |  else$CARET
         |}""".stripMargin
    val after =
      s"""def test = {
         |  val x = if (true) 8
         |          else $CARET
         |}""".stripMargin
    doTest(before, after)
  }

  def testIfElse_EndOfTheFile(): Unit = {
    val before =
      s"""val x =
         |  if (true) 8
         | else$CARET
         |""".stripMargin
    val after =
      s"""val x =
         |  if (true) 8
         |  else $CARET
         |""".stripMargin
    doTest(before, after)
  }

  def testIfElse_LeftMostPosition(): Unit = {
    val before =
      s"""val x =
         |  if (true) 8
         |else$CARET
         |""".stripMargin
    val after =
      s"""val x =
         |  if (true) 8
         |  else $CARET
         |""".stripMargin
    doTest(before, after)
  }

  def testMatchCase(): Unit = {
    val before =
      s"""val x = 5 match {
         |  case 1 => 2
         |    case$CARET
         |}""".stripMargin
    val after =
      s"""val x = 5 match {
         |  case 1 => 2
         |  case $CARET
         |}""".stripMargin
    doTest(before, after)
  }

  def testTryCatch(): Unit = {
    val before =
      s"""def test = {
         |  val x =
         |    try ()
         |  catch$CARET
         |}""".stripMargin
    val after =
      s"""def test = {
         |  val x =
         |    try ()
         |    catch $CARET
         |}""".stripMargin
    doTest(before, after)
  }

  def testTryFinally(): Unit = {
    val before =
      s"""def test = {
         |  val x =
         |    try ()
         |  finally$CARET
         |}""".stripMargin
    val after =
      s"""def test = {
         |  val x =
         |    try ()
         |    finally $CARET
         |}""".stripMargin
    doTest(before, after)
  }

  def testAfterIncompleteInfixOperatorInFunctionBody(): Unit = checkGeneratedTextAfterTypingText(
    s"""class B {
       |  def foo = 42 +
       |  $CARET
       |}""".stripMargin,
    s"""class B {
       |  def foo = 42 +
       |      $CARET
       |}""",
    "    "
  )
}
