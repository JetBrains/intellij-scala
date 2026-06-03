package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class ApparentResultTypeRefinementInspectionTest extends ScalaInspectionTestBase {
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ApparentResultTypeRefinementInspection]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.result.type.refinement")

  private val hint = ScalaInspectionBundle.message("insert.missing.assignment")


  def test(): Unit = {
    checkTextHasError(
      text =
        s"""
           |trait T {
           |  def test(): ${START}T {}$END
           |}
         """.stripMargin
    )

    testQuickFix(
      text =
        s"""
           |trait T {
           |  def test(): T$CARET {}
           |}
         """.stripMargin,
      expected =
        s"""
           |trait T {
           |  def test(): T = {}
           |}
         """.stripMargin,
      hint
    )
  }

  def test_ok(): Unit = {

    checkTextHasNoErrors(
      text =
        s"""
           |trait T {
           |  def test(): T = {}
           |}
         """.stripMargin
    )
  }
}