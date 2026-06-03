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

  // SCL-25190
  def testNestedPartialFunctionCaseClause(): Unit =
    doTestWithEmptyLastLine(
      s"""object NestedPFFormat {
         |  def example(pf: PartialFunction[String, PartialFunction[Int, Boolean]]): Int = 42
         |
         |  example {
         |    case "foo" =>
         |      case${CARET}1 => true
         |  }
         |}""".stripMargin,
      s"""object NestedPFFormat {
         |  def example(pf: PartialFunction[String, PartialFunction[Int, Boolean]]): Int = 42
         |
         |  example {
         |    case "foo" =>
         |    case ${CARET}1 => true
         |  }
         |}""".stripMargin
    )

  def testNotANestedPartialFunctionCaseClause(): Unit =
    doTestWithEmptyLastLine(
      s"""object NestedPFFormat {
         |  def example(pf: PartialFunction[String, PartialFunction[Int, Boolean]]): Int = 42
         |
         |  example {
         |    case "foo" =>
         |      println(42)
         |      case${CARET}1 => true
         |  }
         |}""".stripMargin,
      s"""object NestedPFFormat {
         |  def example(pf: PartialFunction[String, PartialFunction[Int, Boolean]]): Int = 42
         |
         |  example {
         |    case "foo" =>
         |      println(42)
         |    case ${CARET}1 => true
         |  }
         |}""".stripMargin
    )

  // SCL-25190
  def testNestedPartialFunctionCaseClause_DeepNesting(): Unit =
    doTestWithEmptyLastLine(
      s"""object NestedPFFormat {
         |  def example(
         |    pf: PartialFunction[String, PartialFunction[Int, PartialFunction[Int, PartialFunction[Int, PartialFunction[Int, Boolean]]]]]
         |  ): Int = 42
         |
         |  example {
         |    case "foo" =>
         |      case 1 =>
         |        case 2 =>
         |          case 3 =>
         |            case$CARET
         |  }
         |}""".stripMargin,
      s"""object NestedPFFormat {
         |  def example(
         |    pf: PartialFunction[String, PartialFunction[Int, PartialFunction[Int, PartialFunction[Int, PartialFunction[Int, Boolean]]]]]
         |  ): Int = 42
         |
         |  example {
         |    case "foo" =>
         |      case 1 =>
         |        case 2 =>
         |          case 3 =>
         |          case $CARET
         |  }
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

  override def testNestedPartialFunctionCaseClause_DeepNesting(): Unit =
    doTestWithEmptyLastLine(
      s"""object NestedPFFormat {
         |  def example(
         |    pf: PartialFunction[String, PartialFunction[Int, PartialFunction[Int, PartialFunction[Int, PartialFunction[Int, Boolean]]]]]
         |  ): Int = 42
         |
         |  example {
         |    case "foo" =>
         |      case 1 =>
         |        case 2 =>
         |          case 3 =>
         |            case$CARET
         |  }
         |}""".stripMargin,
      s"""object NestedPFFormat {
         |  def example(
         |    pf: PartialFunction[String, PartialFunction[Int, PartialFunction[Int, PartialFunction[Int, PartialFunction[Int, Boolean]]]]]
         |  ): Int = 42
         |
         |  example {
         |    case "foo" =>
         |      case 1 =>
         |        case 2 =>
         |          case 3 =>
         |    case $CARET
         |  }
         |}""".stripMargin
    )
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

  override def testTryCatch_Unindented(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    try ()
         |  catch$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x =
         |    try ()
         |  catch $CARET
         |}""".stripMargin
    )

  override def testTryFinally_Unindented(): Unit =
    doTestWithEmptyLastLine(
      s"""def test = {
         |  val x =
         |    try ()
         |  finally$CARET
         |}""".stripMargin,
      s"""def test = {
         |  val x =
         |    try ()
         |  finally $CARET
         |}""".stripMargin
    )
}
