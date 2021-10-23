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

  def testSimpleCallsWithArguments(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val z = simpleMethodWithArgs(15, 12) + simpleMethodWithArgs(2, 9)
      |z == 10
      |""".stripMargin
  })(
    "z == 10" -> ConditionAlwaysTrue
  )

  def testCallsWithNamedAndDefaultParameters(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val z = methodWithDefaultParam(15, 12) + methodWithDefaultParam(2, 9, 4)
      |z == 21
      |""".stripMargin
  })(
    "z == 21" -> ConditionAlwaysTrue
  )
}
