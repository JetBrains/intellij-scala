package org.jetbrains.plugins.scala.codeInspection.redundantBlock

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class RedundantBlockInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[RedundantBlockInspection]
  override protected val description =
    ScalaInspectionBundle.message("redundant.braces.in.case.clause") + " or " +
      ScalaInspectionBundle.message("the.enclosing.block.is.redundant")

  override protected def descriptionMatches(s: String): Boolean =
    s == ScalaInspectionBundle.message("redundant.braces.in.case.clause") ||
      s == ScalaInspectionBundle.message("the.enclosing.block.is.redundant")

  def test_in_case_class(): Unit = {
    checkTextHasError(
      s"""
         |x match {
         |  case _ =>
         |    $START{$END
         |      x
         |    $START}$END
         |}
         |""".stripMargin,
      allowAdditionalHighlights = true
    )

    testQuickFix(
      """
        |x match {
        |  case _ =>
        |    {
        |      x
        |    }
        |}
        |""".stripMargin,
      """
        |x match {
        |  case _ =>
        |    x
        |}
        |""".stripMargin,
      ScalaInspectionBundle.message("remove.redundant.braces")
    )
  }

  def test_identifier_in_block_without_spaces(): Unit = checkTextHasError(
    s"""
       |$START{${END}x$START}$END
       |""".stripMargin
  )

  def test_identifier_in_block_with_preceding_spaces(): Unit = checkTextHasNoErrors(
    s"""
       |{ x}
       |""".stripMargin
  )

  def test_identifier_in_block_with_following_spaces(): Unit = checkTextHasNoErrors(
    s"""
       |{x }
       |""".stripMargin
  )

  def test_identifier_in_block_surrounded_by_spaces(): Unit = checkTextHasNoErrors(
    s"""
       |{ x }
       |""".stripMargin
  )

  def test_this(): Unit = checkTextHasError(
    s"""
       |$START{${END}t${START}hi${END}s$START}$END
       |""".stripMargin
  )

  def test_in_interpolated_string(): Unit = checkTextHasError(
    s"""
       |s"before $$$START{${END}x$START}$END after"
       |""".stripMargin
  )

  def test_in_interpolated_string_with_whitespaces(): Unit = {
    checkTextHasError(
      s"""
         |s"before $$$START{$END ${START}x$END $START}$END after"
         |""".stripMargin
    )

    testQuickFix(
      """
        |s"before ${ x } after"
        |""".stripMargin,
      """
        |s"before $x after"
        |""".stripMargin,
      ScalaInspectionBundle.message("unwrap.the.expression")
    )
  }

  def test_in_string_with_multi_statement(): Unit = checkTextHasNoErrors(
    """
      |s"before ${def x = 3; x} after"
      |""".stripMargin
  )

  def test_in_interpolated_string_with_following_identifier(): Unit = checkTextHasNoErrors(
    """
      |s"before ${x}after"
      |""".stripMargin
  )

  // SCL-17140
  def test_non_char_identifier_in_interpolated_string(): Unit = checkTextHasNoErrors(
    """
      |s"before ${|} after"
      |""".stripMargin
  )
}
