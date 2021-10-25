package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class UnaryPrefixExpressionsDfaTest extends ScalaDfaTestBase {

  def testNumericUnaryOperators(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |(-3) + 5 == 2
      |5 - -3 == 9 // false
      |val y = -2
      |val z = -0
      |z == 0
      |+0 == -0
      |y < 0
      |y < -3 // false
      |5 + +5 + (+9) + -2 == 17
      |""".stripMargin
  })(
    "(-3) + 5 == 2" -> ConditionAlwaysTrue,
    "5 - -3 == 9" -> ConditionAlwaysFalse,
    "+0 == -0" -> ConditionAlwaysTrue,
    "z == 0" -> ConditionAlwaysTrue,
    "y < 0" -> ConditionAlwaysTrue,
    "y < -3" -> ConditionAlwaysFalse,
    "5 + +5 + (+9) + -2 == 17" -> ConditionAlwaysTrue,
    "z" -> ExpressionAlwaysZero,
    "+0" -> ExpressionAlwaysZero,
    "-0" -> ExpressionAlwaysZero,
    "-0" -> ExpressionAlwaysZero
  )

  def testLogicalUnaryOperators(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val y = 200
      |val x = if (!(y < 100 || y <= 150) && (!(y > 300) && y > 20)) -9 else -3
      |x != -9
      |val p1 = 3 > 99
      |val p2 = if (!p1) 9 >= 9 else 9 > 9
      |val u = if (p2) 0 else 1
      |u == 1
      |
      |val w = if (p2 && arg3) 2 else 7
      |w == 2
      |w < 8
      |""".stripMargin
  })(
    "!(y < 100 || y <= 150)" -> ConditionAlwaysTrue,
    "!(y < 100 || y <= 150) && (!(y > 300) && y > 20)" -> ConditionAlwaysTrue,
    "!(y > 300)" -> ConditionAlwaysTrue,
    "!p1" -> ConditionAlwaysTrue,
    "3 > 99" -> ConditionAlwaysFalse,
    "9 >= 9" -> ConditionAlwaysTrue,
    "if (!p1) 9 >= 9 else 9 > 9" -> ConditionAlwaysTrue,
    "u == 1" -> ConditionAlwaysFalse,
    "x != -9" -> ConditionAlwaysFalse,
    "y < 100" -> ConditionAlwaysFalse,
    "y <= 150" -> ConditionAlwaysFalse,
    "y > 20" -> ConditionAlwaysTrue,
    "w < 8" -> ConditionAlwaysTrue,
    "if (p2) 0 else 1" -> ExpressionAlwaysZero,
    "u" -> ExpressionAlwaysZero
  )
}
