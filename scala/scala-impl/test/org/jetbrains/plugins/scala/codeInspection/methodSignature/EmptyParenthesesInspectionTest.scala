package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}


class EmptyParenthesesInspectionTest extends ScalaInspectionTestBase {
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[AccessorLikeMethodInspection.EmptyParentheses]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.accessor.empty.parenthesis")

  private val hint = ScalaInspectionBundle.message("redundant.parentheses")


  def test(): Unit = {
    checkTextHasError(
      text = s"def ${START}getStuff$END(): Boolean = true"
    )

    testQuickFix(
      text = s"def get${CARET}Stuff(): Boolean = true",
      expected = "def getStuff: Boolean = true",
      hint
    )
  }


  def test_ok(): Unit = {
    checkTextHasNoErrors(
      text = s"def getStuff(): Unit = ()"
    )

    checkTextHasNoErrors(
      text = s"def stuff(): Int = 0"
    )
  }

  def test_with_base_class(): Unit = {
    checkTextHasError(
      s"""
        |class Impl extends Base {
        |  def getStuff: Int = 0
        |}
        |
        |trait Base {
        |  def ${START}getStuff$END(): Int
        |}
      """.stripMargin
    )
  }
}
