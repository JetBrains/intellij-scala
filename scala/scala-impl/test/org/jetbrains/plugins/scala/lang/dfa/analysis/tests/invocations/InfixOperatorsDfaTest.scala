package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class InfixOperatorsDfaTest extends ScalaDfaTestBase {

  def testRelationalOperators(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |3 > 2
      |22 <= 3 * 7
      |""".stripMargin
  })(List(
    "3 > 2" -> "Condition is always true",
    "22 <= 3 * 7" -> "Condition is always false"
  ))
}
