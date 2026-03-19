package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class IndentedCaseClauseFormatterTest extends AbstractScalaFormatterTestBase {

  def testIndentedCaseClauseTreatAsSameLevelClause(): Unit = doTextTest(
    """object Example {
      |  def example(pf: PartialFunction[String, Boolean]): Int = 42
      |
      |  example {
      |    case "foo" =>
      |    case "bar" => true
      |  }
      |}
      |""".stripMargin
  )

  def testIndentedCaseClauseTreatAsSameLevelClauseDespiteExtraIndent(): Unit = doTextTest(
    """object Example {
      |  def example(pf: PartialFunction[String, PartialFunction[Int, Boolean]]): Int = 42
      |
      |  example {
      |    case "foo" =>
      |      case 1 => true
      |  }
      |}
      |""".stripMargin,
    """object Example {
      |  def example(pf: PartialFunction[String, PartialFunction[Int, Boolean]]): Int = 42
      |
      |  example {
      |    case "foo" =>
      |    case 1 => true
      |  }
      |}
      |""".stripMargin
  )
}
