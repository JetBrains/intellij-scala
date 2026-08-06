package org.jetbrains.plugins.scala.codeInspection.syntacticSimplification

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ScalaUnnecessarySemicolonInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnnecessarySemicolonInspection]

  override val description = ScalaInspectionBundle.message("unnecessary.semicolon")

  def testInStatements(): Unit = {
    checkTextHasError(
      s"""val x = 3$START;$END
         |println(x)
         |""".stripMargin
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
    checkTextHasError(s"val x = 3$START;$END")

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
