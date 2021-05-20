package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}

class ParameterlessInspectionTest extends ScalaQuickFixTestBase {
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnitMethodInspection.Parameterless]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.unit.parameterless")

  private val hint = ScalaInspectionBundle.message("empty.parentheses")


  def test_function_style(): Unit = {
    checkTextHasError(
      text = s"def ${START}foo$END: Unit"
    )

    testQuickFix(
      text = s"def f${CARET}oo: Unit",
      expected = "def foo(): Unit",
      hint
    )
  }

  def test_procedure_style(): Unit = {
    checkTextHasError(
      text = s"def ${START}foo$END"
    )

    testQuickFix(
      text = s"def f${CARET}oo",
      expected = "def foo()",
      hint
    )
  }
}
