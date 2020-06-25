package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}

/**
 * Inspection UnitMethodInspection.ExplicitType is deprecated!!!
 */
class ExplicitTypeInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}
  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnitMethodInspection.ExplicitType]

  protected override val description: String =
    ScalaInspectionBundle.message("method.signature.unit.explicit.type")

  private val hint = "Remove redundant type annotation"


  def test(): Unit = {
    checkTextHasError(
      text = s"def foo(): ${START}Unit$END"
    )

    testQuickFix(
      text = s"def foo(): Un${CARET}it",
      expected = "def foo()",
      hint
    )
  }
}
