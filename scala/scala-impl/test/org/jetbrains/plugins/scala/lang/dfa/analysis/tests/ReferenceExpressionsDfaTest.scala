package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages.{ConditionAlwaysFalse, ConditionAlwaysTrue}
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaCollectionAccessProblem.indexOutOfBoundsProblem

class ReferenceExpressionsDfaTest extends ScalaDfaTestBase {

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
      |    val p2 = Person(30, grades)
      |    p1.age == 22
      |    p2.age == 30
      |    p1.grades(5)
      |  }
      |}
      |""".stripMargin
  }(
    "p1.age == 22" -> ConditionAlwaysTrue,
    "p2.age == 30" -> ConditionAlwaysTrue,
    "p1.grades(5)" -> indexOutOfBoundsProblem.alwaysMessage,
  )

  def testAccessingRegularClassParameters(): Unit = test {
    """
      |object Test {
      |  class Person(val age: Int, val grades: List[Int])
      |
      |  def main(p2: Person): Int = {
      |    val grades = List(3, 4, 1)
      |    val p1 = new Person(22, grades)
      |    val p2 = new Person(30, grades)
      |    p1 == p2 // test if p1 is not assigned with a wrong type
      |    p2.age == 30
      |    p1.age == 22
      |    p1.grades(5)
      |  }
      |}
      |""".stripMargin
  }(
    "p2.age == 30" -> ConditionAlwaysTrue,
    "p1.age == 22" -> ConditionAlwaysTrue,
    "p1.grades(5)" -> indexOutOfBoundsProblem.alwaysMessage,
  )

  def testCopyingReferenceValueDirectly(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x = 15
      |val y = x
      |val z = y
      |z == 15
      |""".stripMargin
  })(
    "z == 15" -> ConditionAlwaysTrue
  )

  def testSuppressingWarningsForSomeNamedReferences(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x = 2
      |
      |val verbose = true
      |if (verbose) {
      |  val argCount = 0
      |  foo(verbose, argCount)
      |} else {
      |  3
      |}
      |
      |x == 2
      |""".stripMargin
  })(
    "x == 2" -> ConditionAlwaysTrue
  )

  def testNestedQualifiedExpressions(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |case class User(age: Int, bestFriend: Person)
      |
      |val u1 = User(20, new Person(33))
      |
      |val p1 = new Person(55)
      |p1.id == 55
      |val u2 = User(23, p1)
      |u2.bestFriend == p1
      |
      |u1.bestFriend.id > 30
      |
      |u1.bestFriend.id == u2.bestFriend.id
      |u1.bestFriend.id < u2.bestFriend.id
      |
      |u1.age == 23
      |
      |""".stripMargin
  })(
    "p1.id == 55" -> ConditionAlwaysTrue,
    "u2.bestFriend == p1" -> ConditionAlwaysTrue,
    "u1.age == 23" -> ConditionAlwaysFalse
  )
}
