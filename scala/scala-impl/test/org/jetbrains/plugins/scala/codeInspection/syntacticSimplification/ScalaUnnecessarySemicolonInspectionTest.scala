package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaQuickFixTestBase}

class ScalaUnnecessarySemicolonInspectionTest extends ScalaQuickFixTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnnecessarySemicolonInspection]

  override val description = ScalaInspectionBundle.message("unnecessary.semicolon")

  def testInStatements(): Unit = {
    checkTextHasError(
      s"""val x = 3$START;$END
         |println(x)
         |"""
    )

    testQuickFix(
      """val x = 3;
        |println(x)
        |""".stripMargin,
      """val x = 3
        |println(x)
        |""".stripMargin,
      "Remove unnecessary semicolon"
    )
  }

  def testAtEnd(): Unit = {
    checkTextHasError(
      s"""val x = 3$START;$END
         |"""
    )

    testQuickFix(
      """val x = 3;
        |""".stripMargin,
      """val x = 3
        |""".stripMargin,
      "Remove unnecessary semicolon"
    )
  }

  def testOnSameLine(): Unit =
    checkTextHasNoErrors("a; b")
}
