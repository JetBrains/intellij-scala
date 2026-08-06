package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

class Scala3IndentedCaseClauseFormatterTest extends Scala3FormatterBaseTest {

  def testIndentedCaseClauseTreatAsNestedPartialFunction(): Unit = doTextTest(
    """object Example {
      |  def example(pf: PartialFunction[String, PartialFunction[Int, Boolean]]): Int = 42
      |
      |  example {
      |    case "foo" =>
      |      case 1 => true
      |  }
      |}
      |""".stripMargin
  )

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
}
