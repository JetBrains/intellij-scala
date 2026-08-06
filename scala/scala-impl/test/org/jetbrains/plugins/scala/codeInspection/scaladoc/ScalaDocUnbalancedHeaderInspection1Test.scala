package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ScalaDocUnbalancedHeaderInspection1Test extends ScalaInspectionTestBase {

  override protected val classOfInspection = classOf[ScalaDocUnbalancedHeaderInspection]
  override protected val description = "Header tags unbalanced"
  private val hint = "Balance Header"

  def testBalanceHeaderFix_SingleLine(): Unit = {
    testQuickFix(
      """/** =header 1=== */""",
      """/** =header 1= */""",
      hint
    )

    testQuickFix(
      """/** ==header 1==== */""",
      """/** ==header 1== */""",
      hint
    )

    testQuickFix(
      """/** ==header 1==== some other text */""",
      """/** ==header 1== some other text */""",
      hint
    )
  }

  def testBalanceHeaderFix_MultiLine(): Unit = {
    testQuickFix(
      """/**
        | * ==header 1====
        | */""".stripMargin,
      """/**
        | * ==header 1==
        | */""".stripMargin,
      hint
    )

    testQuickFix(
      """/**
        | * ==header 1==== some text after
        | */""".stripMargin,
      """/**
        | * ==header 1== some text after
        | */""".stripMargin,
      hint
    )
  }
}
