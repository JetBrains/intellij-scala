package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaQuickFixTestBase}


/**
 * Inspection UnitMethodInspection.ExplicitAssignment is deprecated!!!
 */
class ExplicitAssignmentInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnitMethodInspection.ExplicitAssignment]

  protected override val description: String =
    InspectionBundle.message("method.signature.unit.explicit.assignment")

  private val hint = "Remove redundant equals sign"


  def test(): Unit = {
    checkTextHasError(
      text = s"def foo() $START=$END { println() }"
    )

    testQuickFix(
      text = s"def foo() $CARET= { println() }",
      expected = "def foo() { println() }",
      hint
    )
  }
}
