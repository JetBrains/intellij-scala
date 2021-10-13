package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class DefinitionsDfaTest extends ScalaDfaTestBase {

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
}
