package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class IfStatementsDfaTest extends ScalaDfaTestBase {

  def testRegularIfs(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x = if (3 < 2 && 5 <= 7) {
      |  5 + 2 - 20
      |} else if (12 == 13 || 13 != 5 || false) {
      |  7 + 3 * 6 % 5
      |} else {
      |  9 * 3
      |}
      |
      |x == 10
      |x >= 11
      |""".stripMargin
  })(
    "3 < 2" -> ConditionAlwaysFalse,
    "3 < 2 && 5 <= 7" -> ConditionAlwaysFalse,
    "12 == 13" -> ConditionAlwaysFalse,
    "13 != 5" -> ConditionAlwaysTrue,
    "12 == 13 || 13 != 5 || false" -> ConditionAlwaysTrue,
    "x == 10" -> ConditionAlwaysTrue,
    "x >= 11" -> ConditionAlwaysFalse
  )
}
