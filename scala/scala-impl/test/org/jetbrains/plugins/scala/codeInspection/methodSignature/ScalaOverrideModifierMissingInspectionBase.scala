package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaQuickFixTestBase}

abstract class ScalaOverrideModifierMissingInspectionBase(keyword: String) extends ScalaQuickFixTestBase {
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection =
    classOf[ScalaOverrideModifierMissingInspection]
  override protected val description =
    InspectionBundle.message("method.signature.override.modifier.missing")
  private val hint = "Add `override` modifier"

  def test1(): Unit = {
    val text =
      s"""
         |trait A {
         | $keyword sample: Int
         |}
         |
         |class B extends A {
         | 1;$START$keyword sample: Int = 1$END
         |}
        """
    checkTextHasError(text)

    testQuickFix(text,
      expected =
        s"""
           |trait A {
           | $keyword sample: Int
           |}
           |
           |class B extends A {
           | 1;override $keyword sample: Int = 1
           |}
        """,
      hint
    )
  }

}

class FunctionScalaOverrideModifierMissingInspectionTest extends ScalaOverrideModifierMissingInspectionBase("def")
class ValueScalaOverrideModifierMissingInspectionTest extends ScalaOverrideModifierMissingInspectionBase("val")
class VariableScalaOverrideModifierMissingInspectionTest extends ScalaOverrideModifierMissingInspectionBase("var")
