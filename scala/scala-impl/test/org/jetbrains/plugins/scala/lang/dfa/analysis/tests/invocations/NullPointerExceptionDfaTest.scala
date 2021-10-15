package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class NullPointerExceptionDfaTest extends ScalaDfaTestBase {

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

  def testNormalInvocationsOnNullReferences(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val list: List[Int] = null
      |list.map(foo)
      |""".stripMargin
  })(
    "list.map(foo)" -> InvocationNullPointer
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
    "3 +*: p" -> InvocationNullPointer
  )
}
