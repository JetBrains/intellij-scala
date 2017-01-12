package org.jetbrains.plugins.scala
package codeInspection.booleans

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 4/24/13
 */
class SimplifyBooleanInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  val annotation = "Simplify boolean expression"

  private def check(text: String) {
    checkTextHasError(text, annotation, classOf[SimplifyBooleanInspection])
  }

  private def testFix(text: String, result: String, hint: String) {
    testQuickFix(text.replace("\r", ""), result.replace("\r", ""), hint, classOf[SimplifyBooleanInspection])
  }

  private def checkHasNoErrors(text: String) {
    checkTextHasNoErrors(text, annotation, classOf[SimplifyBooleanInspection])
  }

  def test_NotTrue() {
    val selectedText = s"$START!true$END"
    check(selectedText)

    val text = "!true"
    val result = "false"
    val hint = "Simplify !true"
    testFix(text, result, hint)
  }

  def test_TrueEqualsA() {
    val selectedText =
      s"""val a = true
         |${START}true == a$END""".stripMargin
    check(selectedText)

    val text =
      """val a = true
        |true == a""".stripMargin
    val result = """val a = true
                   |a""".stripMargin
    val hint = "Simplify true == a"
    testFix(text, result, hint)
  }

  def test_TrueAndA() {
    val selectedText =
      s"""val a = true
         |${START}true && a$END""".stripMargin
    check(selectedText)

    val text =
       """val a = true
          |true && a""".stripMargin
    val result = """val a = true
                    |a""".stripMargin
    val hint = "Simplify true && a"
    testFix(text, result, hint)
  }

  def test_AOrFalse() {
    val selectedText = s"""val a = true
                          |${START}a | false$END""".stripMargin
    check(selectedText)

    val text = """val a = true
                   |a | false""".stripMargin
    val result =  """val a = true
                    |a""".stripMargin
    val hint = "Simplify a | false"
    testFix(text, result, hint)
  }

  def test_ExternalExpression() {
    val selectedText = s"""
                          |val a = true
                          |${START}true && (a || false)$END
      """.stripMargin
    check(selectedText)

    val text = s"""
        |val a = true
        |true && (a || false)""".stripMargin
    val result = """
        |val a = true
        |a""".stripMargin
    val hint = "Simplify true && (a || false)"
    testFix(text, result, hint)
  }

  def test_InternalExpression() {
    val selectedText =
      s"""
         |val a = true
         |true && ($START<caret>a || false$END)
      """.stripMargin
    check(selectedText)

    val text = s"""
        |val a = true
        |true && (<caret>a || false)
      """.stripMargin
    val result = s"""
        |val a = true
        |true && a
      """.stripMargin
    val hint = "Simplify a || false"
    testFix(text, result, hint)
  }

  def test_TrueNotEqualsA() {
    val selectedText =  s"""val a = true
                           |val flag: Boolean = ${START}true != a$END""".stripMargin
    check(selectedText)

    val text = s"""val a = true
                  |val flag: Boolean = true != a""".stripMargin
    val result = """val a = true
                    |val flag: Boolean = !a""".stripMargin
    val hint = "Simplify true != a"
    testFix(text, result, hint)
  }

  def test_SimplifyInParentheses() {
    val selectedText = s"""val a = true
                          |!(${START}true != a$END)""".stripMargin
    check(selectedText)

    val text = """val a = true
                  |!(true != a)""".stripMargin
    val result = """val a = true
                   |!(!a)""".stripMargin
    val hint = "Simplify true != a"
    testFix(text, result, hint)
  }

  def test_TrueAsAny() {
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
      """.stripMargin.replace("\r", "").trim

    checkHasNoErrors(text)
  }

  def testParentheses(): Unit = {
    testFix(
      "true<caret> && (2 - 1) * 0 == 0",
      "(2 - 1) * 0 == 0",
      "Simplify true && (2 - 1) * 0 == 0")
  }
}
