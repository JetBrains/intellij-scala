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
      |""".stripMargin
  })(
    "x1 == 9" -> ConditionAlwaysTrue,
    "x1 == abs(3 + 6)" -> ConditionAlwaysTrue,
    "x2 == 300l" -> ConditionAlwaysTrue
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
