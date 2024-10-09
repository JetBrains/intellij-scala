package org.jetbrains.plugins.scala
package codeInspection
package parentheses

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.parameters.TypedParameterWithoutParenthesisInspection
import org.jetbrains.plugins.scala.extensions._

class TypedParameterWithoutParenthesisInspectionTest extends InspectionSeverityForcingScalaInspectionTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[TypedParameterWithoutParenthesisInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("typed.parameter.without.parenthesis.in.function.literal")

  override protected def forcedInspectionSeverity: HighlightDisplayLevel =
    HighlightDisplayLevel.WARNING

  val hint = ScalaInspectionBundle.message("surround.with.parenthesis")


  def test_typed_brace(): Unit = {
    val selected =
      s"""
         |test { ${START}a: Int$END => a }
         |""".stripMargin.withNormalizedSeparator
    checkTextHasError(selected)
  }

  def test_typed_brace_quickfix(): Unit = {
    val text =
      s"""
         |test { ${CARET_MARKER}a: Int => a }
         |""".stripMargin.withNormalizedSeparator
    val result =
      """
        |test { (a: Int) => a }
        |""".stripMargin.withNormalizedSeparator
    testQuickFix(text, result, hint)
  }

  def test_untyped_parenthesis(): Unit =
    checkTextHasNoErrors(
      """
        |test(a: Int => a)
        |""".stripMargin)

  def test_already_has_parenthesis(): Unit =
    checkTextHasNoErrors(
      """
        |test { (a: Int) => a }
        |""".stripMargin
    )

  def test_untyped_braced(): Unit =
    checkTextHasNoErrors(
      """
        |test { a => a }
        |""".stripMargin
    )

  def test_parenthesis(): Unit =
    checkTextHasNoErrors(
      """
        |test(a => a)
        |""".stripMargin
    )
}
