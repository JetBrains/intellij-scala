package org.jetbrains.plugins.scala.lang.actions.editor

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class SpaceInsertTestBase extends EditorTypeActionTestBase {

  override protected def typedChar: Char = ' '

  def testIfElse_Unindented(): Unit =
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

  def testIfElse_Indented(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    if (true) 8
         |    else$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x =
         |    if (true) 8
         |    else $CARET
         |}""".stripMargin
    )

  def testIfElse_AlignIfElseEnabled_IfKeywordDoesntStartTheLine(): Unit = {
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
         |  else$CARET
         |""".stripMargin,
      s"""val x =
         |  if (true) 8
         |  else $CARET
         |""".stripMargin
    )

  def testIfElse_EndOfTheFile_Unindented(): Unit =
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

  def testTryCatch_Unindented(): Unit =
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

  def testTryCatch_Indented(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    try ()
         |    catch$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x =
         |    try ()
         |    catch $CARET
         |}""".stripMargin
    )

  def testTryFinally_Unindented(): Unit =
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

  def testTryFinally_Indented(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    try ()
         |    finally$CARET
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

final class SpaceInsertTest_2_13 extends SpaceInsertTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.languageLevel == LatestScalaVersions.Scala_2_13.languageLevel
}

final class SpaceInsertTest_3 extends SpaceInsertTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.languageLevel == LatestScalaVersions.Scala_3.languageLevel

  override def testIfElse_Unindented(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    if (true) 8
         |  else$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x =
         |    if (true) 8
         |  else $CARET
         |}""".stripMargin
    )

  override def testIfElse_EndOfTheFile_Unindented(): Unit =
    doTestWithEmptyLastLine(
      s"""val x =
         |  if (true) 8
         |else$CARET
         |""".stripMargin,
      s"""val x =
         |  if (true) 8
         |else $CARET
         |""".stripMargin
    )

//TODO: uncomment when SCL-25203 is fixed
//  override def testTryCatch_Unindented(): Unit =
//    doTestWithEmptyLastLine(
//      s"""def test = {
//         |  val x =
//         |    try ()
//         |  catch$CARET
//         |}""".stripMargin,
//      s"""def test = {
//         |  val x =
//         |    try ()
//         |  catch $CARET
//         |}""".stripMargin
//    )
//
//  override def testTryFinally_Unindented(): Unit =
//    doTestWithEmptyLastLine(
//      s"""def test = {
//         |  val x =
//         |    try ()
//         |  finally$CARET
//         |}""".stripMargin,
//      s"""def test = {
//         |  val x =
//         |    try ()
//         |  finally $CARET
//         |}""".stripMargin
//    )
}
