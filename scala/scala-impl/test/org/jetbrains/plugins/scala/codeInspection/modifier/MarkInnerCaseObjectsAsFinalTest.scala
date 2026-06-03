package org.jetbrains.plugins.scala.codeInspection.modifier

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInspection.modifiers.MarkInnerCaseObjectsAsFinal
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class MarkInnerCaseObjectsAsFinalTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[MarkInnerCaseObjectsAsFinal]

  override protected val description: String =
    ScalaInspectionBundle.message("mark.inner.case.objects.as.final")

  def testCaseObject(): Unit = testQuickFix(
    s"""
       |object Outer {
       |  case ${START}object$END Inner
       |}
       |""".stripMargin,
    """
      |object Outer {
      |  final case object Inner
      |}
      |""".stripMargin,
    ScalaInspectionBundle.message("add.modifier", "final")
  )

  def testToplevelCaseObject(): Unit = checkTextHasNoErrors(
    s"""
       |case object Test
       |""".stripMargin
  )

  def testFinalInnerCaseObject(): Unit = checkTextHasNoErrors(
    s"""
       |object Outer {
       |  final case object Inner
       |}
       |""".stripMargin
  )

  def testFinalInnerCaseClass(): Unit = checkTextHasNoErrors(
    s"""
       |object Outer {
       |  final case class Inner()
       |}
       |""".stripMargin
  )
}
