package org.jetbrains.plugins.scala.codeInspection.scaladoc

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class ScalaDocUnbalancedHeaderInspection2Test extends ScalaInspectionTestBase {

  override protected val classOfInspection = classOf[ScalaDocUnbalancedHeaderInspection]
  override protected val description = "All text from header closing tag to end of line will be lost"
  private val hintMoveAfter = "Move text after header closing to new line"

  def testMoveTextAfterHeader(): Unit =
    testQuickFix(
      """/** ==header 1== some text after */""",
      """/** ==header 1==
        | * some text after */""".stripMargin,
      hintMoveAfter
    )

  def testMoveTextAfterHeader_SeveralLeadingSpaces(): Unit =
    testQuickFix(
      """/** ==header 1==     some text after */""",
      """/** ==header 1==
        | * some text after */""".stripMargin,
      hintMoveAfter
    )

  def testMoveTextAfterHeader_TextWithSyntaxElements(): Unit =
    testQuickFix(
      """/** ==header 1== some `text` __after__ */""",
      """/** ==header 1==
        | * some `text` __after__ */""".stripMargin,
      hintMoveAfter
    )

  def testMoveTextAfterUnbalancedHeader(): Unit =
    testQuickFix(
      """/** ===unbalanced header==== some text after */""",
      """/** ===unbalanced header====
        | * some text after */""".stripMargin,
      hintMoveAfter
    )

  def testNoMoveTextAfterHeaderInspection_NoTextAfterHeader(): Unit = {
    checkNotFixable(
      """/**==header==*/
        |class A""".stripMargin,
      hintMoveAfter
    )

    checkNotFixable(
      """/** ==header== */
        |class A""".stripMargin,
      hintMoveAfter
    )

    checkNotFixable(
      """/** ==header==
        | */
        |class A""".stripMargin,
      hintMoveAfter
    )
  }

  def testNoMoveTextAfterHeaderInspection_NoAsterisksAfterHeader(): Unit = {
    checkNotFixable(
      """/**
        | * ==header
        |
        |
        | */
        |class A""".stripMargin,
      hintMoveAfter
    )

    checkNotFixable(
      """/**
        | * ==header==
        |
        |
        | */
        |class A""".stripMargin,
      hintMoveAfter
    )
  }
}