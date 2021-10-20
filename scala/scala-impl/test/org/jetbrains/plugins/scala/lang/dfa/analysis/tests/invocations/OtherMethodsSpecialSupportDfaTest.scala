package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class OtherMethodsSpecialSupportDfaTest extends ScalaDfaTestBase {

  def testAbsFunction(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x1 = abs(-9)
      |x1 == 9
      |x1 == abs(3 + 6)
      |val x2 = abs(-300L)
      |x2 == 300l
      |val x3 = abs(4.33)
      |x3 > 4.2
      |
      |if (arg1 > 30) {
      |  if (abs(-arg1) > 25) {
      |    println("Hi")
      |  }
      |}
      |""".stripMargin
  })(
    "x1 == 9" -> ConditionAlwaysTrue,
    "x1 == abs(3 + 6)" -> ConditionAlwaysTrue,
    "x2 == 300l" -> ConditionAlwaysTrue,
    "x3 > 4.2" -> ConditionAlwaysTrue,
    "abs(-arg1) > 25" -> ConditionAlwaysTrue,
  )

  def testMinAndMaxFunctions(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x1 = max(max(8, max(6, 2)), -5)
      |x1 > 7
      |val x2 = min(max(8L, max(6L, 2L)), -5L)
      |x2 != 6L
      |val x3 = min(max(8.33, max(6.21133, 2.0002)), 5.32)
      |x3 > 6.2
      |""".stripMargin
  })(
    "x1 > 7" -> ConditionAlwaysTrue,
    "x2 != 6L" -> ConditionAlwaysTrue,
    "x3 > 6.2" -> ConditionAlwaysFalse
  )

  def testSqrtFunction(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x1 = sqrt(9)
      |x1 > 3.1
      |
      |val x2 = sqrt(8)
      |x2 <= 1.7
      |
      |val x3 = sqrt(7.53)
      |x3 >= 1.3
      |""".stripMargin
  })(
    "x1 > 3.1" -> ConditionAlwaysFalse,
    "x2 <= 1.7" -> ConditionAlwaysFalse,
    "x3 >= 1.3" -> ConditionAlwaysTrue
  )

  def testJavaMethodsWithCustomHandlers(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = Math.sqrt(9)
      |val list = util.List.of(2, 7)
      |list.add(9) // List.of returns an unmodifiable list, so this can't affect it
      |val y = list.indexOf(3)
      |x == 3.0
      |x == 3.1
      |y == 2
      |""".stripMargin
  })(
    "x == 3.0" -> ConditionAlwaysTrue,
    "x == 3.1" -> ConditionAlwaysFalse,
    "y == 2" -> ConditionAlwaysFalse
  )
}
