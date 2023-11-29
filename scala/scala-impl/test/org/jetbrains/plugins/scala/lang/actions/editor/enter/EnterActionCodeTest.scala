package org.jetbrains.plugins.scala.lang.actions.editor.enter

import com.intellij.application.options.CodeStyle
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class EnterActionCodeTest extends EditorActionTestBase
  with Scala2AndScala3EnterActionCommonTests {

  def testInsideEmptyParameterClauses(): Unit = {
    getScalaCodeStyleSettings.INDENT_FIRST_PARAMETER = false

    val before =
      s"""def foo($CARET)"""
    val after =
      s"""def foo(
         |  $CARET
         |)""".stripMargin

    getCommonCodeStyleSettings.ALIGN_MULTILINE_PARAMETERS = true
    checkGeneratedTextAfterEnter(before, after)

    getCommonCodeStyleSettings.ALIGN_MULTILINE_PARAMETERS = false
    checkGeneratedTextAfterEnter(before, after)
  }

  def testInsideEmptyParameterClauses_1(): Unit = {
    getScalaCodeStyleSettings.INDENT_FIRST_PARAMETER = true

    val before =
      s"""def foo($CARET)"""

    getCommonCodeStyleSettings.ALIGN_MULTILINE_PARAMETERS = true
    checkGeneratedTextAfterEnter(before,
      s"""def foo(
         |       $CARET
         |       )""".stripMargin)

    getCommonCodeStyleSettings.ALIGN_MULTILINE_PARAMETERS = false
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

  // SCL-18488
  def testEnterInAlignedIfElse(): Unit = {
    CodeStyle.getSettings(getProject).getCustomSettings(classOf[ScalaCodeStyleSettings]).ALIGN_IF_ELSE = true
    checkGeneratedTextAfterEnter(
      s"""class Test {
         |  if (true) "yay"
         |  else if (false)$CARET "YAY"
         |}
         |""".stripMargin,
      s"""class Test {
         |  if (true) "yay"
         |  else if (false)
         |         $CARET"YAY"
         |}
         |""".stripMargin
    )
  }
}
