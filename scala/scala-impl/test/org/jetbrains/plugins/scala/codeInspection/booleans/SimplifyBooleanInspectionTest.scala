package org.jetbrains.plugins.scala
package codeInspection
package booleans

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

class SimplifyBooleanInspectionTest extends ScalaInspectionTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[SimplifyBooleanInspection]

  override protected val description =
    "Simplify boolean expression"

  def test_NotTrue(): Unit = {
    val selectedText = s"$START!true$END"
    checkTextHasError(selectedText)

    val text = "!true"
    val result = "false"
    val hint = "Simplify !true"
    testQuickFix(text, result, hint)
  }

  def test_TrueEqualsA(): Unit = {
    val selectedText =
      s"""val a = true
         |${START}true == a$END""".stripMargin
    checkTextHasError(selectedText)

    val text =
      """val a = true
        |true == a""".stripMargin
    val result = """val a = true
                   |a""".stripMargin
    val hint = "Simplify true == a"
    testQuickFix(text, result, hint)
  }

  def test_TrueAndA(): Unit = {
    val selectedText =
      s"""val a = true
         |${START}true && a$END""".stripMargin
    checkTextHasError(selectedText)

    val text =
       """val a = true
          |true && a""".stripMargin
    val result = """val a = true
                    |a""".stripMargin
    val hint = "Simplify true && a"
    testQuickFix(text, result, hint)
  }

  def test_AOrFalse(): Unit = {
    val selectedText = s"""val a = true
                          |${START}a | false$END""".stripMargin
    checkTextHasError(selectedText)

    val text = """val a = true
                   |a | false""".stripMargin
    val result =  """val a = true
                    |a""".stripMargin
    val hint = "Simplify a | false"
    testQuickFix(text, result, hint)
  }

  def test_ExternalExpression(): Unit = {
    val selectedText = s"""
                          |val a = true
                          |${START}true && (a || false)$END
      """.stripMargin
    checkTextHasError(selectedText, allowAdditionalHighlights = true)

    val text = s"""
        |val a = true
        |true && (a || false)""".stripMargin
    val result = """
        |val a = true
        |a""".stripMargin
    val hint = "Simplify true && (a || false)"
    testQuickFix(text, result, hint)
  }

  def test_InternalExpression(): Unit = {
    val selectedText =
      s"""
         |val a = true
         |true && (${START}a || false$END)
      """.stripMargin
    checkTextHasError(selectedText, allowAdditionalHighlights = true)

    val text = s"""
                  |val a = true
                  |true && (${CARET_MARKER}a || false)
      """.stripMargin
    val result = s"""
        |val a = true
        |true && a
      """.stripMargin
    val hint = "Simplify a || false"
    testQuickFix(text, result, hint)
  }

  def test_TrueNotEqualsA(): Unit = {
    val selectedText =  s"""val a = true
                           |val flag: Boolean = ${START}true != a$END""".stripMargin
    checkTextHasError(selectedText)

    val text = s"""val a = true
                  |val flag: Boolean = true != a""".stripMargin
    val result = """val a = true
                    |val flag: Boolean = !a""".stripMargin
    val hint = "Simplify true != a"
    testQuickFix(text, result, hint)
  }

  def test_SimplifyInParentheses(): Unit = {
    val selectedText = s"""val a = true
                          |!(${START}true != a$END)""".stripMargin
    checkTextHasError(selectedText, allowAdditionalHighlights = true)

    val text = """val a = true
                  |!(true != a)""".stripMargin
    val result = """val a = true
                   |!(!a)""".stripMargin
    val hint = "Simplify true != a"
    testQuickFix(text, result, hint)
  }

  def test_TrueAsAny(): Unit = {
    val text =
      """
        |def trueAsAny: Any = {
        |  true
        |}
        |if (trueAsAny == true) {
        |  println("true")
        |} else {
        |  println("false")
        |}
        |
      """.stripMargin

    checkTextHasNoErrors(text)
  }

  def testParentheses(): Unit = {
    testQuickFix(
      s"true$CARET_MARKER && (2 - 1) * 0 == 0",
      "(2 - 1) * 0 == 0",
      "Simplify true && (2 - 1) * 0 == 0")
  }
}
