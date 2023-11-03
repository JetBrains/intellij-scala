package org.jetbrains.plugins.scala.codeInspection.redundantClassParamClause

import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class RedundantClassParamClauseInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[RedundantClassParamClauseInspection]
  override protected val description = ScalaInspectionBundle.message("empty.parameter.clause.is.redundant")

  private val QUICKFIX = ScalaInspectionBundle.message("remove.redundant.parameter.clause")

  def test_no_param_clause(): Unit =
    checkTextHasNoErrors("class Test")

  def test_case_class(): Unit = {
    checkTextHasNoErrors("case class Test()")
    checkTextHasNoErrors("case class Test()()")
  }

  def test_normal_parameter_clause(): Unit = {
    checkTextHasError(s"class Test$START()$END")
    checkTextHasError(s"class Test $START()$END")
    checkTextHasError(s"class Test private$START()$END")
    checkTextHasError(s"class Test private $START()$END")
    checkTextHasError(s"class Test$START()$END {}")
  }

  def test_with_comment(): Unit =
    checkTextHasError(s"class Test$START(/* test */)$END")

  def test_multiple_clauses(): Unit =
    checkTextHasError(s"class Test$START()()$END")

  def test_implicit_clause(): Unit = {
    checkTextHasNoErrors(s"case class Test(implicit blub: Int)")
    checkTextHasNoErrors(s"case class Test(implicit blub: Int)()")
  }

  def test_incomplete_clause(): Unit =
    checkTextHasError(s"class Test$START($END")

  def test_quickfix_on_one_clause(): Unit =
    testQuickFix(
      s"class Test($CARET)",
      "class Test",
      QUICKFIX,
    )

  def test_quickfix_on_two_clause(): Unit =
    testQuickFix(
      s"class Test($CARET)()",
      "class Test",
      QUICKFIX,
    )

  def test_multi_quickfix(): Unit =
    testQuickFixAllInFile(
      """
        |class Test()
        |class Blub()
        |""".stripMargin,
      """
        |class Test
        |
        |class Blub
        |""".stripMargin,
      QUICKFIX,
    )
}
