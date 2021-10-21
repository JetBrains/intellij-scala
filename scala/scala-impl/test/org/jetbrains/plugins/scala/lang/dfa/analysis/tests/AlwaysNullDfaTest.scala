package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class AlwaysNullDfaTest extends ScalaDfaTestBase {

  def testReferencesToNullIfSimpleReference(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x: Student = null
      |x.age
      |""".stripMargin
  })(
    "x" -> ExpressionAlwaysNull
  )

  def testReferencesToNullIfInvocation(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x: List[Int] = if (2 <= 3) null else List(2, 3)
      |x.distinct
      |""".stripMargin
  })(
    "x" -> ExpressionAlwaysNull,
    "if (2 <= 3) null else List(2, 3)" -> ExpressionAlwaysNull,
    "2 <= 3" -> ConditionAlwaysTrue
  )

  def testNormalInvocationsOnNullReferences(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val list: List[Int] = null
      |list.map(foo)
      |""".stripMargin
  })(
    "list" -> ExpressionAlwaysNull
  )

  def testNullReferencesInSugaredCalls(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |class Person(age: Int) {
      |  def +*:(plus: Int): Int = age + plus
      |}
      |
      |val p: Person = null
      |3 +*: p
      |""".stripMargin
  })(
    "p" -> ExpressionAlwaysNull
  )
}
