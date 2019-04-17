package org.jetbrains.plugins.scala.lang.actions.editor.enter

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

class EnterActionCodeTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  def testInsideEmptyParameterClauses(): Unit = {
    getScalaSettings.INDENT_FIRST_PARAMETER = false

    val before =
      s"""def foo($CARET_MARKER)"""
    val after =
      s"""def foo(
         |  $CARET_MARKER
         |)""".stripMargin

    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = true
    checkGeneratedTextAfterEnter(before, after)

    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false
    checkGeneratedTextAfterEnter(before, after)
  }

  def testInsideEmptyParameterClauses_1(): Unit = {
    getScalaSettings.INDENT_FIRST_PARAMETER = true

    val before =
      s"""def foo($CARET_MARKER)"""

    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = true
    checkGeneratedTextAfterEnter(before,
      s"""def foo(
         |       $CARET_MARKER
         |       )""".stripMargin)

    getCommonSettings.ALIGN_MULTILINE_PARAMETERS = false
    checkGeneratedTextAfterEnter(before,
      s"""def foo(
         |  $CARET_MARKER
         |)""".stripMargin)
  }

}
