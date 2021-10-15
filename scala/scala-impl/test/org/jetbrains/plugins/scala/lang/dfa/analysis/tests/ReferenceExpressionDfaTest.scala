package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages.{ConditionAlwaysTrue, InvocationNullPointer}
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

  def testReferencesToNullIfSimpleReference(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |case class Person(age: Int)
      |
      |val x: Person = null
      |x.age
      |""".stripMargin
  })(
    "x.age" -> InvocationNullPointer
  )

  def testReferencesToNullIfInvocation(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x: List[Int] = if (2 <= 3) null else List(2, 3)
      |x.distinct
      |""".stripMargin
  })(
    "x.distinct" -> InvocationNullPointer,
    "2 <= 3" -> ConditionAlwaysTrue
  )
}
