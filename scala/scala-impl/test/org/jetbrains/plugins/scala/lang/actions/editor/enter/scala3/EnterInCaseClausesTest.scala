package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import org.jetbrains.plugins.scala.ScalaVersion

class EnterInCaseClausesWithBracesTest_Scala2 extends EnterInCaseClausesWithBracesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version < ScalaVersion.Latest.Scala_3_0
}

class EnterInCaseClausesWithBracesTest_Scala3 extends EnterInCaseClausesWithBracesTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0
}

abstract class EnterInCaseClausesWithBracesTestBase extends DoEditorStateTestOps {

  def testLastCaseClause_WithoutCode(): Unit =
    doEnterTest(
      s"""1 match {
        |  case 1 =>
        |  case _ =>$CARET
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |    $CARET
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |
        |    $CARET
        |}""".stripMargin,
    )

  def testLastCaseClause_WithCode(): Unit =
    doEnterTest(
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |    42$CARET
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |    42
        |    $CARET
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |    42
        |
        |    $CARET
        |}""".stripMargin,
    )

  def testLastCaseClause_WithCodeOnSameLine(): Unit =
    doEnterTest(
      s"""1 match {
        |  case 1 =>
        |  case _ => 42$CARET
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ => 42
        |  $CARET
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ => 42
        |
        |  $CARET
        |}""".stripMargin,
    )

  def testMiddleCaseClause_WithoutCode(): Unit =
    doEnterTest(
      s"""1 match {
        |  case 1 =>
        |  case _ =>$CARET
        |  case _ =>
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |    $CARET
        |  case _ =>
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |
        |    $CARET
        |  case _ =>
        |}""".stripMargin,
    )

  def testMiddleCaseClause_WithCode(): Unit =
    doEnterTest(
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |    42$CARET
        |  case _ =>
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |    42
        |    $CARET
        |  case _ =>
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ =>
        |    42
        |
        |    $CARET
        |  case _ =>
        |}""".stripMargin,
    )

  def testMiddleCaseClause_WithCodeOnSameLine(): Unit =
    doEnterTest(
      s"""1 match {
        |  case 1 =>
        |  case _ => 42$CARET
        |  case _ =>
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ => 42
        |  $CARET
        |  case _ =>
        |}""".stripMargin,
      s"""1 match {
        |  case 1 =>
        |  case _ => 42
        |
        |  $CARET
        |  case _ =>
        |}""".stripMargin,
    )
}
