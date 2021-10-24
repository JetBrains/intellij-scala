package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class AlwaysZeroDfaTest extends ScalaDfaTestBase {

  def testReportingAlwaysZero(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |5 + -3 + 2 + 1 - 7 + 2
      |3 + 0 + -0 + 1
      |val x = if (arg3) 2 - 2 + 0 else 64 % 2
      |val y = if (arg3) 2 - 2 + 0 else 64 % 2
      |val z = y + 3
      |val w = 0L
      |w
      |val student = Student(y, Nil)
      |student.age < 13
      |val t = z / 2 - 1 + y
      |t != 0
      |0
      |-0
      |+0
      |""".stripMargin
  })(
    "5 + -3 + 2 + 1 - 7 + 2" -> ExpressionAlwaysZero,
    "-0" -> ExpressionAlwaysZero,
    "+0" -> ExpressionAlwaysZero,
    "-0" -> ExpressionAlwaysZero,
    "2 - 2 + 0" -> ExpressionAlwaysZero,
    "2 - 2" -> ExpressionAlwaysZero,
    "64 % 2" -> ExpressionAlwaysZero,
    "2 - 2 + 0" -> ExpressionAlwaysZero,
    "64 % 2" -> ExpressionAlwaysZero,
    "2 - 2" -> ExpressionAlwaysZero,
    "student.age" -> ExpressionAlwaysZero,
    "student.age < 13" -> ConditionAlwaysTrue,
    "w" -> ExpressionAlwaysZero,
    "y" -> ExpressionAlwaysZero,
    "y" -> ExpressionAlwaysZero,
    "t" -> ExpressionAlwaysZero,
    "t != 0" -> ConditionAlwaysFalse,
    "z / 2 - 1" -> ExpressionAlwaysZero,
    "z / 2 - 1 + y" -> ExpressionAlwaysZero,
    "if (arg3) 2 - 2 + 0 else 64 % 2" -> ExpressionAlwaysZero,
    "if (arg3) 2 - 2 + 0 else 64 % 2" -> ExpressionAlwaysZero
  )
}
