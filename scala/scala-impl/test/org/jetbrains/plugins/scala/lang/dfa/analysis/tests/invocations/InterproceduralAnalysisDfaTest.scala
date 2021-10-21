package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages.ConditionAlwaysTrue
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class InterproceduralAnalysisDfaTest extends ScalaDfaTestBase {

  def testSimpleCallsWithoutArguments(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = verySimpleMethod()
      |val y = verySimpleMethod()
      |x == 5
      |y == 5
      |""".stripMargin
  })(
    "x == 5" -> ConditionAlwaysTrue,
    "y == 5" -> ConditionAlwaysTrue
  )
}
