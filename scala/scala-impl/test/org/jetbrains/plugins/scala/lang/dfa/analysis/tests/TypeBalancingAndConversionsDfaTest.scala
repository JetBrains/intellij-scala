package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class TypeBalancingAndConversionsDfaTest extends ScalaDfaTestBase {

  def testImplicitConversionsInBinaryOperators(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |1 + 2L == 3L
      |1L + 2 == 3L
      |val x = 3L
      |x == 3L
      |4L * 4 == 16L
      |3 > 2.7
      |2.2 > 10
      |3 > 2L
      |5L < 3
      |5 == 5L
      |2.5 > 2
      |""".stripMargin
  })(
    "1 + 2L == 3L" -> ConditionAlwaysTrue,
    "1L + 2 == 3L" -> ConditionAlwaysTrue,
    "x == 3L" -> ConditionAlwaysTrue,
    "4L * 4 == 16L" -> ConditionAlwaysTrue,
    "3 > 2.7" -> ConditionAlwaysTrue,
    "2.2 > 10" -> ConditionAlwaysFalse,
    "3 > 2L" -> ConditionAlwaysTrue,
    "5L < 3" -> ConditionAlwaysFalse,
    "5 == 5L" -> ConditionAlwaysTrue,
    "2.5 > 2" -> ConditionAlwaysTrue
  )

  def testImplicitConversionsInOtherExpressions(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val y: Long = 3
      |y == 3L
      |y == 3
      |y < 2L
      |y == 3L
      |
      |val w = 3
      |w == 3L
      |w == 3
      |w < 2L
      |w == 3L
      |
      |val x = if (arg1 > 2) 3 else 3L
      |x == 3
      |x == 3L
      |
      |var z = 3L
      |z = 4
      |z == 4
      |z == 4L
      |""".stripMargin
  })(
    "y == 3L" -> ConditionAlwaysTrue,
    "y == 3" -> ConditionAlwaysTrue,
    "y < 2L" -> ConditionAlwaysFalse,
    "y == 3L" -> ConditionAlwaysTrue,
    "w == 3L" -> ConditionAlwaysTrue,
    "w == 3" -> ConditionAlwaysTrue,
    "w < 2L" -> ConditionAlwaysFalse,
    "w == 3L" -> ConditionAlwaysTrue,
    "x == 3" -> ConditionAlwaysTrue,
    "x == 3L" -> ConditionAlwaysTrue,
    "z == 4" -> ConditionAlwaysTrue,
    "z == 4L" -> ConditionAlwaysTrue
  )

  def testNotReportingDoublesAsZero(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = 2
      |
      |val z = 0.3
      |z > 5
      |
      |x == 2
      |""".stripMargin
  })(
    "x == 2" -> ConditionAlwaysTrue,
    "z > 5" -> ConditionAlwaysFalse
  )
}
