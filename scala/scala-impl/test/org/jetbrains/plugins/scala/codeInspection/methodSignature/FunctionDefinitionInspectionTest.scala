package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}


class FunctionDefinitionInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnitMethodInspection.FunctionDefinition]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.unit.functional.definition")

  private val hint = "Remove redundant type annotation and equals sign"


  def test(): Unit = {
    checkTextHasError(
      text = s"def foo(): ${START}Unit$END = { println() }"
    )

    testQuickFix(
      text = s"def foo(): Un${CARET}it = { println() }",
      expected = "def foo() { println() }",
      hint
    )
  }
}
