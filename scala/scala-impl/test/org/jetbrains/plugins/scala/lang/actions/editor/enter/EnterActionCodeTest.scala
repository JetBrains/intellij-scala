package org.jetbrains.plugins.scala.lang.actions.editor.enter

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

class EnterActionCodeTest extends EditorActionTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}

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

}
