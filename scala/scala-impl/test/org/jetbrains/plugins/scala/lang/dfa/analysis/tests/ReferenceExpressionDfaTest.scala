package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages.{ConditionAlwaysTrue, InvocationIndexOutOfBounds}
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class ReferenceExpressionDfaTest extends ScalaDfaTestBase {

  def testIgnoringReferencesToMethodArgs(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |var x = 15
      |val z = arg1 + x * arg2
      |x == 15
      |val zz = arg333 + x
      |x == 14 + 1
      |""".stripMargin
  })(
    "x == 15" -> ConditionAlwaysTrue
  )

  def testAccessingCaseClassParameters(): Unit = test {
    """
      |object Test {
      |  case class Person(age: Int, grades: List[Int])
      |
      |  def main(): Int = {
      |    val grades = List(3, 4, 1)
      |    val p1 = Person(22, grades)
      |    p1.age >= 20
      |    p1.grades(5)
      |  }
      |}
      |""".stripMargin
  }(
    "p1.age >= 20" -> ConditionAlwaysTrue,
    "p1.grades(5)" -> InvocationIndexOutOfBounds
  )
}
