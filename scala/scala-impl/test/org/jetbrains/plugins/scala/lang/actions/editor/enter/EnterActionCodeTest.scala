package org.jetbrains.plugins.scala.lang.actions.editor.enter

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class EnterActionCodeTest extends EditorActionTestBase
  with Scala2AndScala3EnterActionCommonTests {

  def testInsideEmptyParameterClauses(): Unit = {
    getScalaSettings.INDENT_FIRST_PARAMETER = false

    val before =
      s"""def foo($CARET)"""
    val after =
      s"""def foo(
         |  $CARET
         |)""".stripMargin

    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = true
    checkGeneratedTextAfterEnter(before, after)

    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false
    checkGeneratedTextAfterEnter(before, after)
  }

  def testInsideEmptyParameterClauses_1(): Unit = {
    getScalaSettings.INDENT_FIRST_PARAMETER = true

    val before =
      s"""def foo($CARET)"""

    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = true
    checkGeneratedTextAfterEnter(before,
      s"""def foo(
         |       $CARET
         |       )""".stripMargin)

    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false
    checkGeneratedTextAfterEnter(before,
      s"""def foo(
         |  $CARET
         |)""".stripMargin)
  }

  def testInsertUnmatchedBraceAfterAnonymousClass(): Unit = {
    val before =
      s"""class A {
         |  def bar = {${CARET}new T {
         |    override def foo: Unit = ()
         |  }
         |}
       """.stripMargin
    val after =
      s"""class A {
         |  def bar = {
         |    ${CARET}new T {
         |      override def foo: Unit = ()
         |    }
         |  }
         |}
       """.stripMargin
    checkGeneratedTextAfterEnter(before, after)
  }

  def testInsertUnmatchedBraceAfterTripleQuestionMark(): Unit = {
    val before =
      s"""class A {
         |  def bar = {$CARET???
         |}
       """.stripMargin
    val after =
      s"""class A {
         |  def bar = {
         |    $CARET???
         |  }
         |}
       """.stripMargin
    checkGeneratedTextAfterEnter(before, after)
  }

  def testIndentAfterCaseClause(): Unit = checkGeneratedTextAfterEnter(
    s"""1 match {
       |   case 0 =>$CARET
       |   case 1 =>
       |}
       |""".stripMargin,
    s"""1 match {
       |   case 0 =>
       |     $CARET
       |   case 1 =>
       |}
       |""".stripMargin
  )

  def testIndentAfterCaseClauseMultiCaret(): Unit = checkGeneratedTextAfterEnter(
    s"""1 match {
       |  case 0 =>$CARET
       |  case 1 =>$CARET
       |}
       |""".stripMargin,
    s"""1 match {
       |  case 0 =>
       |    $CARET
       |  case 1 =>
       |    $CARET
       |}
       |""".stripMargin
  )

  def testIndentInsideCaseClauseInPartialFunction(): Unit = checkGeneratedTextAfterEnter(
    s"""Seq(1, 2, 3).collect {
       |  case x: Int =>$CARET
       |}
       |""".stripMargin,
    s"""Seq(1, 2, 3).collect {
       |  case x: Int =>
       |    $CARET
       |}
       |""".stripMargin
  )

  def testIndentInsideCaseClauseInPartialFunction_1(): Unit = checkGeneratedTextAfterEnter(
    s"""Seq(1, 2, 3).collect {
       |  case x: Int =>
       |    val y = x + 1$CARET
       |}
       |""".stripMargin,
    s"""Seq(1, 2, 3).collect {
       |  case x: Int =>
       |    val y = x + 1
       |    $CARET
       |}
       |""".stripMargin
  )

  def testIndentInsideCaseClauseInPartialFunction_WithCaseOnPreviousLine(): Unit = checkGeneratedTextAfterEnter(
    s"""Seq(1, 2, 3).collect { case x: Int =>
       |  val y = x + 1$CARET
       |}
       |""".stripMargin,
    s"""Seq(1, 2, 3).collect { case x: Int =>
       |  val y = x + 1
       |  $CARET
       |}
       |""".stripMargin
  )
}
