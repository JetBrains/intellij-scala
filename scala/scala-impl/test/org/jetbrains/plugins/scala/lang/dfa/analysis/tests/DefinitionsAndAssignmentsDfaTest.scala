package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class DefinitionsAndAssignmentsDfaTest extends ScalaDfaTestBase {

  def testDefiningSimpleValuesAndVariables(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val booleanVal = 3 > 2
      |var x = 3 * 8 + 15 // 39
      |val z = if (booleanVal) x * 7 + 3 // 276
      |else 5 - x
      |z == 276
      |z > 300
      |""".stripMargin
  })(
    "3 > 2" -> ConditionAlwaysTrue,
    "booleanVal" -> ConditionAlwaysTrue,
    "z == 276" -> ConditionAlwaysTrue,
    "z > 300" -> ConditionAlwaysFalse
  )

  def testReassigningVars(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |var y = 5 * 2
      |var x = 9
      |x > 10
      |x = 8
      |x > 11
      |x = 14
      |x > 12
      |x = y
      |x == 10
      |""".stripMargin
  })(
    "x > 10" -> ConditionAlwaysFalse,
    "x > 11" -> ConditionAlwaysFalse,
    "x > 12" -> ConditionAlwaysTrue,
    "x == 10" -> ConditionAlwaysTrue
  )
}
