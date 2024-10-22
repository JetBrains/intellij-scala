package org.jetbrains.plugins.scala
package codeInspection
package modifier

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInspection.modifiers.RedundantFinalOnToplevelObjectInspection
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class RedundantFinalOnToplevelObjectInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[RedundantFinalOnToplevelObjectInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("final.modifier.is.redundant.for.toplevel.objects")

  def testRedundantFinalModifier(): Unit = testQuickFix(
    s"""
       |${START}final$END object Test
       |""".stripMargin,
    """
      |object Test
      |""".stripMargin,
    ScalaInspectionBundle.message("remove.modifier", "final")
  )

  def testNoFinalModifier(): Unit = checkTextHasNoErrors(
    s"""
       |object Test
       |""".stripMargin
  )

  def testMissingNonToplevelFinalModifier(): Unit = checkTextHasNoErrors(
    s"""
       |object Test {
       |  final object Test
       |}
       |""".stripMargin
  )
}
